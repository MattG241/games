/* ============================================================
   TokFeed — link resolvers
   ------------------------------------------------------------
   Turns a pasted page URL into something the feed can play.
   Everything here runs in the browser: no server, no yt-dlp,
   so the app works on a phone with nothing else installed.

   A resolved item looks like:
     {
       src:      original pasted URL
       kind:     'video' | 'embed'
       url:      direct media URL            (kind === 'video')
       alt:      [fallback media URLs]       (kind === 'video')
       embed:    iframe URL                  (kind === 'embed')
       manual:   true if it can't be autoplayed/controlled
       poster, title, author, duration, provider, resolvedAt
     }
   ============================================================ */
(function (global) {
  'use strict';

  var TF = global.TF = global.TF || {};

  /* ---------------- generic helpers ---------------- */

  var MEDIA_RE = /\.(mp4|m4v|webm|ogv|ogg|mov|mkv|m3u8|mpd)(\?|#|$)/i;

  // Public CORS relays, used only if a direct call fails. Both are
  // best-effort third parties — the direct call is what normally works.
  var PROXIES = [
    function (u) { return 'https://api.codetabs.com/v1/proxy?quest=' + encodeURIComponent(u); },
    function (u) { return 'https://api.allorigins.win/raw?url=' + encodeURIComponent(u); }
  ];

  function sleep(ms) {
    return new Promise(function (r) { setTimeout(r, ms); });
  }

  /** fetch + JSON parse with a hard timeout. */
  function fetchJSON(url, timeoutMs) {
    var ctl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = setTimeout(function () { if (ctl) ctl.abort(); }, timeoutMs || 15000);

    return fetch(url, {
      signal: ctl ? ctl.signal : undefined,
      credentials: 'omit',
      referrerPolicy: 'no-referrer'
    }).then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.text();
    }).then(function (text) {
      try {
        return JSON.parse(text);
      } catch (e) {
        throw new Error('Bad JSON from ' + hostOf(url));
      }
    }).finally(function () {
      clearTimeout(timer);
    });
  }

  /** Try a JSON endpoint directly, then through the CORS relays. */
  function fetchJSONResilient(url, timeoutMs) {
    return fetchJSON(url, timeoutMs).catch(function (firstErr) {
      var chain = Promise.reject(firstErr);
      PROXIES.forEach(function (wrap) {
        chain = chain.catch(function () { return fetchJSON(wrap(url), timeoutMs); });
      });
      return chain.catch(function () { throw firstErr; });
    });
  }

  function hostOf(url) {
    try { return new URL(url).hostname.replace(/^www\./, ''); }
    catch (e) { return ''; }
  }

  function isHost(url, re) {
    var h = hostOf(url);
    return re.test(h);
  }

  /* ---------------- link parsing ---------------- */

  /**
   * Pull URLs out of arbitrary pasted text. Share sheets paste things like
   * "look at this 😂 https://vm.tiktok.com/ZSxxx/", and people paste
   * comma-, space- or newline-separated lists. Order is preserved,
   * duplicates dropped.
   */
  function parseLinks(text) {
    var out = [];
    var seen = Object.create(null);
    var re = /https?:\/\/[^\s,"'<>()\[\]]+/gi;
    var m;
    while ((m = re.exec(String(text || '')))) {
      var url = m[0].replace(/[.,;:!?)]+$/, '');   // strip trailing punctuation
      if (!seen[url]) {
        seen[url] = true;
        out.push(url);
      }
    }
    return out;
  }

  /* ---------------- TikTok (tikwm) ---------------- */

  var TIKWM_ENDPOINTS = [
    'https://www.tikwm.com/api/',
    'https://tikwm.com/api/'
  ];

  // tikwm's free tier rate-limits to roughly one request per second, so
  // every TikTok lookup goes through one serialised queue.
  var tikQueue = Promise.resolve();
  var TIK_GAP_MS = 1100;

  function tikwmQueued(job) {
    var run = tikQueue.then(function () {
      return job().finally(function () { return sleep(TIK_GAP_MS); });
    });
    // Keep the chain alive even when a job rejects.
    tikQueue = run.catch(function () {});
    return run;
  }

  function absolutise(u) {
    if (!u) return '';
    if (/^https?:\/\//i.test(u)) return u;
    if (u.charAt(0) === '/') return 'https://www.tikwm.com' + u;
    return u;
  }

  function tikwmCall(pageUrl, endpoint, hd) {
    var api = endpoint + '?url=' + encodeURIComponent(pageUrl) + (hd ? '&hd=1' : '');
    return fetchJSONResilient(api, 20000).then(function (json) {
      if (!json || typeof json !== 'object') throw new Error('Empty response');
      if (json.code !== 0 || !json.data) {
        var msg = json.msg || 'lookup failed';
        var err = new Error(msg);
        err.rateLimited = /limit|frequen|too many/i.test(msg);
        throw err;
      }
      return json.data;
    });
  }

  function resolveTikTok(pageUrl, opts) {
    var hd = !opts || opts.preferHd !== false;

    return tikwmQueued(function () {
      var attempt = tikwmCall(pageUrl, TIKWM_ENDPOINTS[0], hd);

      // One retry after a pause if we tripped the rate limiter…
      attempt = attempt.catch(function (err) {
        if (!err.rateLimited) throw err;
        return sleep(2500).then(function () {
          return tikwmCall(pageUrl, TIKWM_ENDPOINTS[0], hd);
        });
      });

      // …then fall back to the bare-domain endpoint.
      attempt = attempt.catch(function (err) {
        return tikwmCall(pageUrl, TIKWM_ENDPOINTS[1], hd).catch(function () { throw err; });
      });

      return attempt;
    }).then(function (d) {
      // `hdplay` and `play` are both watermark-free; `wmplay` is the
      // watermarked one and is only kept as a last resort.
      var candidates = [];
      if (hd) candidates.push(d.hdplay);
      candidates.push(d.play, d.hdplay, d.wmplay);

      var urls = [];
      candidates.forEach(function (u) {
        u = absolutise(u);
        if (u && urls.indexOf(u) === -1) urls.push(u);
      });
      if (!urls.length) throw new Error('No playable URL returned');

      return {
        kind: 'video',
        url: urls[0],
        alt: urls.slice(1),
        poster: absolutise(d.cover || d.origin_cover || ''),
        title: d.title || '',
        author: (d.author && (d.author.nickname || d.author.unique_id)) || '',
        duration: Number(d.duration) || 0,
        provider: 'tikwm'
      };
    });
  }

  /* ---------------- YouTube (iframe embed) ---------------- */

  function youTubeId(url) {
    var m;
    if ((m = url.match(/[?&]v=([A-Za-z0-9_-]{6,})/))) return m[1];
    if ((m = url.match(/youtu\.be\/([A-Za-z0-9_-]{6,})/))) return m[1];
    if ((m = url.match(/\/(?:shorts|embed|live|v)\/([A-Za-z0-9_-]{6,})/))) return m[1];
    return '';
  }

  function resolveYouTube(pageUrl) {
    var id = youTubeId(pageUrl);
    if (!id) return Promise.reject(new Error('Could not read the YouTube video id'));

    // YouTube can't be turned into a direct file in the browser, so we embed
    // the official player instead — it autoplays muted and takes JS API
    // commands, which is enough for the feed to drive it.
    var embed = 'https://www.youtube-nocookie.com/embed/' + id +
      '?enablejsapi=1&autoplay=1&mute=1&playsinline=1&controls=1' +
      '&rel=0&modestbranding=1&loop=1&playlist=' + id;

    var item = {
      kind: 'embed',
      embed: embed,
      poster: 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg',
      title: '',
      author: '',
      provider: 'youtube',
      controllable: true
    };

    // Metadata is a nice-to-have; never let it fail the resolve.
    var meta = 'https://www.youtube.com/oembed?format=json&url=' +
      encodeURIComponent('https://www.youtube.com/watch?v=' + id);

    return fetchJSON(meta, 8000).then(function (j) {
      item.title = j.title || '';
      item.author = j.author_name || '';
      return item;
    }).catch(function () { return item; });
  }

  /* ---------------- Instagram (embed, manual play) ---------------- */

  function resolveInstagram(pageUrl) {
    var m = pageUrl.match(/instagram\.com\/(?:reels?|p|tv)\/([A-Za-z0-9_-]+)/);
    if (!m) return Promise.reject(new Error('Could not read the Instagram post id'));

    // Instagram has no public no-auth API, so the best a purely client-side
    // app can do is embed their player. It will not autoplay — hence `manual`.
    return Promise.resolve({
      kind: 'embed',
      embed: 'https://www.instagram.com/p/' + m[1] + '/embed/captioned/',
      manual: true,
      note: 'Instagram needs a tap to play',
      title: '',
      author: '',
      provider: 'instagram'
    });
  }

  /* ---------------- direct media ---------------- */

  function resolveDirect(pageUrl) {
    return Promise.resolve({
      kind: 'video',
      url: pageUrl,
      alt: [],
      title: '',
      author: '',
      provider: 'direct'
    });
  }

  /* ---------------- user-supplied resolver ---------------- */

  var URL_FIELDS = ['url', 'play', 'video', 'direct', 'videoUrl', 'video_url', 'link', 'src', 'hdplay'];

  function pickUrl(obj, depth) {
    if (!obj || depth > 3) return '';
    if (typeof obj === 'string') return /^https?:\/\//.test(obj) ? obj : '';
    for (var i = 0; i < URL_FIELDS.length; i++) {
      var v = obj[URL_FIELDS[i]];
      if (typeof v === 'string' && /^https?:\/\//.test(v)) return v;
    }
    // Common wrappers: { data: {...} }, { result: {...} }
    var nest = obj.data || obj.result || obj.results;
    return nest ? pickUrl(nest, (depth || 0) + 1) : '';
  }

  function resolveCustom(pageUrl, template) {
    var api = template.indexOf('{url}') !== -1
      ? template.replace('{url}', encodeURIComponent(pageUrl))
      : template + (template.indexOf('?') === -1 ? '?' : '&') + 'url=' + encodeURIComponent(pageUrl);

    return fetchJSON(api, 25000).then(function (json) {
      var url = pickUrl(json, 0);
      if (!url) throw new Error('Custom resolver returned no video URL');
      var d = json.data || json;
      return {
        kind: 'video',
        url: url,
        alt: [],
        poster: d.poster || d.cover || d.thumbnail || '',
        title: d.title || '',
        author: d.author || d.uploader || '',
        duration: Number(d.duration) || 0,
        provider: 'custom'
      };
    });
  }

  /* ---------------- dispatcher ---------------- */

  /**
   * Resolve one pasted link.
   * @param {string} pageUrl
   * @param {{preferHd?: boolean, customResolver?: string}} [opts]
   * @returns {Promise<object>} resolved item
   */
  function resolve(pageUrl, opts) {
    opts = opts || {};
    pageUrl = String(pageUrl || '').trim();

    if (!/^https?:\/\//i.test(pageUrl)) {
      return Promise.reject(new Error('Not a valid http(s) link'));
    }

    var run;
    if (MEDIA_RE.test(pageUrl)) {
      run = resolveDirect(pageUrl);                       // a plain file, nothing to look up
    } else if (isHost(pageUrl, /(^|\.)tiktok\.com$/i)) {
      run = resolveTikTok(pageUrl, opts);
    } else if (isHost(pageUrl, /(^|\.)(youtube\.com|youtu\.be)$/i)) {
      run = resolveYouTube(pageUrl);
    } else if (isHost(pageUrl, /(^|\.)instagram\.com$/i)) {
      run = resolveInstagram(pageUrl);
    } else {
      run = resolveDirect(pageUrl);                       // optimistic: let <video> try
    }

    // A configured custom resolver gets first refusal on everything except
    // links that are already a direct media file.
    if (opts.customResolver && !MEDIA_RE.test(pageUrl)) {
      run = resolveCustom(pageUrl, opts.customResolver).catch(function () { return run; });
    }

    return run.then(function (item) {
      item.src = pageUrl;
      item.host = hostOf(pageUrl);
      item.resolvedAt = Date.now();
      return item;
    });
  }

  TF.resolvers = {
    resolve: resolve,
    parseLinks: parseLinks,
    hostOf: hostOf,
    isDirectMedia: function (u) { return MEDIA_RE.test(u); }
  };

})(window);
