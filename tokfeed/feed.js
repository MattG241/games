/* ============================================================
   TokFeed — the vertical feed / player
   ------------------------------------------------------------
   One slide per item, CSS scroll-snap for the swipe, and an
   IntersectionObserver to decide which slide is "active".
   Only the active video plays; its immediate neighbours are
   preloaded; anything further away is unloaded so a phone
   doesn't hold a dozen decoders open at once.
   ============================================================ */
(function (global) {
  'use strict';

  var TF = global.TF = global.TF || {};

  var PRELOAD = 1;   // how many slides either side get a src
  var KEEP = 2;      // beyond this distance the src is released

  var state = {
    root: null,
    items: [],
    slides: [],
    active: -1,
    muted: true,
    autoAdvance: false,
    io: null,
    raf: 0,
    scrollTimer: 0,
    hooks: {}
  };

  /* ---------------- small DOM helpers ---------------- */

  function el(tag, cls, parent) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (parent) parent.appendChild(n);
    return n;
  }

  function svg(paths, cls) {
    var s = '<svg viewBox="0 0 24 24" class="ico ' + (cls || '') + '" aria-hidden="true">' + paths + '</svg>';
    return s;
  }

  var ICON_PLAY = svg('<path d="M7 4l13 8-13 8z"/>');
  var ICON_PAUSE = svg('<path d="M7 4h4v16H7zM13 4h4v16h-4z"/>');

  function escapeHTML(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function prettySrc(url) {
    try {
      var u = new URL(url);
      var path = u.pathname.length > 34 ? u.pathname.slice(0, 33) + '…' : u.pathname;
      return u.hostname.replace(/^www\./, '') + path;
    } catch (e) {
      return url;
    }
  }

  /* ---------------- slide construction ---------------- */

  function buildSlide(item, index) {
    var slide = el('div', 'slide');
    slide.dataset.i = String(index);

    if (item && item.poster) {
      el('div', 'slide-bg', slide).style.backgroundImage = 'url("' + item.poster.replace(/"/g, '%22') + '")';
    }

    var rec = { node: slide, item: item, index: index, media: null, kind: null };

    if (!item || item.error) {
      renderError(rec, (item && item.error) || 'Could not extract this link');
      state.slides[index] = rec;
      return slide;
    }

    if (item.kind === 'embed') {
      buildEmbed(rec);
    } else {
      buildVideo(rec);
    }

    buildOverlay(rec);
    state.slides[index] = rec;
    return slide;
  }

  function buildVideo(rec) {
    var v = document.createElement('video');
    v.playsInline = true;
    v.setAttribute('playsinline', '');
    v.setAttribute('webkit-playsinline', '');
    v.muted = state.muted;              // property *and* attribute: iOS checks both
    if (state.muted) v.setAttribute('muted', '');
    v.loop = !state.autoAdvance;
    v.preload = 'none';
    v.controls = false;
    if (rec.item.poster) v.poster = rec.item.poster;

    rec.urlIndex = 0;
    rec.urls = [rec.item.url].concat(rec.item.alt || []).filter(Boolean);

    v.addEventListener('waiting', function () { rec.node.classList.add('is-loading'); });
    v.addEventListener('playing', function () {
      rec.node.classList.remove('is-loading', 'is-paused');
    });
    v.addEventListener('canplay', function () { rec.node.classList.remove('is-loading'); });
    v.addEventListener('pause', function () {
      if (rec.index === state.active) rec.node.classList.add('is-paused');
    });
    v.addEventListener('play', function () { rec.node.classList.remove('is-paused'); });
    v.addEventListener('ended', function () {
      if (state.autoAdvance && rec.index === state.active) goTo(rec.index + 1, true);
    });
    v.addEventListener('error', function () { onMediaError(rec); });

    rec.node.appendChild(v);
    rec.media = v;
    rec.kind = 'video';
    el('div', 'slide-spin', rec.node);
  }

  function buildEmbed(rec) {
    var f = document.createElement('iframe');
    f.setAttribute('allow', 'autoplay; encrypted-media; picture-in-picture; fullscreen');
    f.setAttribute('allowfullscreen', '');
    f.setAttribute('referrerpolicy', 'no-referrer');
    f.setAttribute('loading', 'lazy');
    f.title = rec.item.title || 'Embedded video';
    rec.node.appendChild(f);
    rec.media = f;
    rec.kind = 'embed';

    if (rec.item.note) {
      el('div', 'slide-note', rec.node).textContent = rec.item.note;
    }
  }

  function buildOverlay(rec) {
    // Embeds own their own tap handling (the provider's player is inside the
    // iframe), so the tap layer and flash icon are video-only.
    if (rec.kind === 'video') {
      var tap = el('div', 'slide-tap', rec.node);
      tap.addEventListener('click', function () { togglePlay(rec); });

      var flash = el('div', 'slide-flash', rec.node);
      flash.innerHTML = ICON_PAUSE;
      rec.flash = flash;
    }

    var meta = el('div', 'slide-meta', rec.node);
    var item = rec.item;
    var html = '';
    if (item.author) html += '<div class="slide-author">' + escapeHTML(item.author) + '</div>';

    if (item.local) {
      // A phone file has no page to open — just name it.
      html += '<span class="slide-src">' + escapeHTML(item.title || 'Video on this phone') + '</span>';
    } else {
      if (item.title) html += '<div class="slide-title">' + escapeHTML(item.title) + '</div>';
      html += '<a class="slide-src" href="' + escapeHTML(item.src) + '" target="_blank" rel="noopener noreferrer">' +
              escapeHTML(prettySrc(item.src)) + '</a>';
    }
    meta.innerHTML = html;
  }

  function renderError(rec, message) {
    var box = el('div', 'slide-error', rec.node);
    var src = (rec.item && rec.item.src) || '';
    box.innerHTML =
      '<h3>Couldn\'t play this one</h3>' +
      '<p>' + escapeHTML(message) + '</p>' +
      '<div class="row-buttons">' +
        '<button class="btn btn-ghost btn-small" data-act="retry">Retry</button>' +
        (src ? '<a class="btn btn-ghost btn-small" href="' + escapeHTML(src) +
               '" target="_blank" rel="noopener noreferrer">Open original</a>' : '') +
      '</div>';

    var retry = box.querySelector('[data-act="retry"]');
    if (retry) {
      retry.addEventListener('click', function () {
        if (state.hooks.onRetry) state.hooks.onRetry(rec.index);
      });
    }
    rec.errorBox = box;

    // Source line still helps identify which link failed.
    if (src) {
      var meta = el('div', 'slide-meta', rec.node);
      meta.innerHTML = '<a class="slide-src" href="' + escapeHTML(src) +
        '" target="_blank" rel="noopener noreferrer">' + escapeHTML(prettySrc(src)) + '</a>';
    }
  }

  /**
   * A media element failed. Walk the alternate URLs first (tikwm hands back
   * several), then ask the app to re-resolve — CDN links are time-limited
   * and a cached feed can simply have gone stale.
   */
  function onMediaError(rec) {
    if (rec.kind !== 'video') return;
    rec.node.classList.remove('is-loading');

    if (rec.urlIndex < rec.urls.length - 1) {
      rec.urlIndex++;
      rec.media.src = rec.urls[rec.urlIndex];
      rec.media.load();
      if (rec.index === state.active) tryPlay(rec);
      return;
    }

    if (!rec.refreshed && state.hooks.onRefresh) {
      rec.refreshed = true;
      state.hooks.onRefresh(rec.index);
      return;
    }

    rec.media.remove();
    rec.media = null;
    renderError(rec, 'The video link is dead or expired.');
  }

  /* ---------------- media loading window ---------------- */

  function attach(rec) {
    if (!rec || !rec.media) return;
    if (rec.kind === 'video') {
      if (!rec.media.getAttribute('src')) {
        rec.media.src = rec.urls[rec.urlIndex];
      }
      rec.media.preload = 'auto';
    } else if (!rec.media.getAttribute('src')) {
      rec.media.src = rec.item.embed;
    }
  }

  function detach(rec) {
    if (!rec || !rec.media) return;
    if (rec.kind === 'video') {
      if (rec.media.getAttribute('src')) {
        rec.media.pause();
        rec.media.removeAttribute('src');
        rec.media.load();            // actually releases the buffer on mobile
      }
      rec.node.classList.remove('is-paused', 'is-loading');
    } else if (rec.media.getAttribute('src')) {
      rec.media.removeAttribute('src');
      rec.media.src = 'about:blank';
    }
  }

  function updateWindow() {
    for (var i = 0; i < state.slides.length; i++) {
      var d = Math.abs(i - state.active);
      if (d <= PRELOAD) attach(state.slides[i]);
      else if (d > KEEP) detach(state.slides[i]);
    }
  }

  /* ---------------- playback ---------------- */

  function ytCommand(rec, func, args) {
    try {
      rec.media.contentWindow.postMessage(JSON.stringify({
        event: 'command', func: func, args: args || []
      }), '*');
    } catch (e) { /* cross-origin: nothing we can do */ }
  }

  function tryPlay(rec) {
    if (!rec || !rec.media) return;

    if (rec.kind === 'embed') {
      if (rec.item.controllable) ytCommand(rec, state.muted ? 'mute' : 'unMute');
      if (rec.item.controllable) ytCommand(rec, 'playVideo');
      return;
    }

    rec.media.muted = state.muted;
    var p = rec.media.play();
    if (p && p.catch) {
      p.catch(function () {
        // Autoplay was refused — almost always because we're unmuted.
        // Fall back to muted playback rather than showing a dead frame.
        if (!rec.media) return;
        setMuted(true);
        var again = rec.media.play();
        if (again && again.catch) {
          again.catch(function () { rec.node.classList.add('is-paused'); });
        }
      });
    }
  }

  function pauseRec(rec) {
    if (!rec || !rec.media) return;
    if (rec.kind === 'embed') {
      if (rec.item.controllable) ytCommand(rec, 'pauseVideo');
      return;
    }
    rec.media.pause();
    rec.node.classList.remove('is-paused');   // only the active slide shows the icon
  }

  function togglePlay(rec) {
    if (!rec || !rec.media || rec.kind !== 'video') return;
    if (rec.media.paused) {
      tryPlay(rec);
      showFlash(rec, ICON_PLAY);
    } else {
      rec.media.pause();
      showFlash(rec, ICON_PAUSE);
    }
  }

  function showFlash(rec, icon) {
    if (!rec.flash) return;
    rec.flash.innerHTML = icon;
    rec.flash.classList.remove('show');
    void rec.flash.offsetWidth;              // restart the animation
    rec.flash.classList.add('show');
  }

  function setActive(i) {
    if (i === state.active || i < 0 || i >= state.slides.length) return;

    var prev = state.slides[state.active];
    if (prev) {
      pauseRec(prev);
      if (prev.media && prev.kind === 'video') prev.media.currentTime = 0;
    }

    state.active = i;
    updateWindow();

    var rec = state.slides[i];
    if (rec) {
      attach(rec);
      tryPlay(rec);
    }

    if (state.hooks.onIndex) state.hooks.onIndex(i, state.slides.length);
    setProgress(0);
    if (state.hooks.onProgressVisible) {
      state.hooks.onProgressVisible(!!rec && rec.kind === 'video' && !!rec.media);
    }
  }

  function goTo(i, smooth) {
    if (i >= state.slides.length) i = state.autoAdvance ? 0 : state.slides.length - 1;
    if (i < 0) i = 0;
    var rec = state.slides[i];
    if (!rec) return;
    rec.node.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', block: 'start' });
    setActive(i);
  }

  function setMuted(muted) {
    state.muted = !!muted;
    state.slides.forEach(function (rec) {
      if (!rec || !rec.media) return;
      if (rec.kind === 'video') {
        rec.media.muted = state.muted;
      } else if (rec.item.controllable && rec.index === state.active) {
        ytCommand(rec, state.muted ? 'mute' : 'unMute');
      }
    });
    if (state.hooks.onMute) state.hooks.onMute(state.muted);
  }

  /* ---------------- progress ---------------- */

  function setProgress(fraction) {
    if (state.hooks.onProgress) state.hooks.onProgress(Math.max(0, Math.min(1, fraction || 0)));
  }

  function tick() {
    var rec = state.slides[state.active];
    if (rec && rec.kind === 'video' && rec.media && rec.media.duration > 0) {
      setProgress(rec.media.currentTime / rec.media.duration);
    }
    state.raf = requestAnimationFrame(tick);
  }

  function seek(fraction) {
    var rec = state.slides[state.active];
    if (!rec || rec.kind !== 'video' || !rec.media || !rec.media.duration) return;
    rec.media.currentTime = Math.max(0, Math.min(1, fraction)) * rec.media.duration;
    setProgress(fraction);
  }

  /* ---------------- scroll tracking ---------------- */

  function observe() {
    if (state.io) state.io.disconnect();

    state.io = new IntersectionObserver(function (entries) {
      var best = null;
      entries.forEach(function (e) {
        if (!best || e.intersectionRatio > best.intersectionRatio) best = e;
      });
      if (best && best.intersectionRatio >= 0.6) {
        setActive(Number(best.target.dataset.i));
      }
    }, { root: state.root, threshold: [0, 0.3, 0.6, 0.9] });

    state.slides.forEach(function (rec) { if (rec) state.io.observe(rec.node); });

    // Fling scrolling can outrun the observer; settle up after the scroll stops.
    state.root.addEventListener('scroll', onScroll, { passive: true });
  }

  function onScroll() {
    clearTimeout(state.scrollTimer);
    state.scrollTimer = setTimeout(function () {
      var h = state.root.clientHeight || 1;
      var i = Math.round(state.root.scrollTop / h);
      setActive(Math.max(0, Math.min(state.slides.length - 1, i)));
    }, 120);
  }

  /* ---------------- public API ---------------- */

  /**
   * Render items into the feed container.
   * @param {HTMLElement} root
   * @param {Array} items
   * @param {object} opts { muted, autoAdvance, startIndex, hooks:{...} }
   */
  function mount(root, items, opts) {
    opts = opts || {};
    unmount();

    state.root = root;
    state.items = items || [];
    state.slides = new Array(state.items.length);
    state.active = -1;
    state.muted = opts.muted !== false;
    state.autoAdvance = !!opts.autoAdvance;
    state.hooks = opts.hooks || {};

    var frag = document.createDocumentFragment();
    state.items.forEach(function (item, i) { frag.appendChild(buildSlide(item, i)); });
    root.innerHTML = '';
    root.appendChild(frag);
    root.scrollTop = 0;

    observe();
    setActive(Math.max(0, Math.min(state.items.length - 1, opts.startIndex || 0)));
    if (state.raf) cancelAnimationFrame(state.raf);
    state.raf = requestAnimationFrame(tick);
  }

  /** Swap one slide in place after a re-resolve. */
  function replaceItem(index, item) {
    var rec = state.slides[index];
    if (!rec) return;
    var old = rec.node;
    state.items[index] = item;

    var fresh = buildSlide(item, index);
    old.parentNode.replaceChild(fresh, old);
    if (state.io) {
      state.io.unobserve(old);
      state.io.observe(fresh);
    }
    if (index === state.active) {
      state.active = -1;          // force setActive to redo the work
      setActive(index);
    } else {
      updateWindow();
    }
  }

  function unmount() {
    if (state.io) { state.io.disconnect(); state.io = null; }
    if (state.root) state.root.removeEventListener('scroll', onScroll);
    if (state.raf) { cancelAnimationFrame(state.raf); state.raf = 0; }
    clearTimeout(state.scrollTimer);
    state.slides.forEach(detach);
    state.slides = [];
    state.items = [];
    state.active = -1;
  }

  function pauseAll() {
    state.slides.forEach(pauseRec);
  }

  function resume() {
    var rec = state.slides[state.active];
    if (rec) tryPlay(rec);
  }

  TF.feed = {
    mount: mount,
    unmount: unmount,
    replaceItem: replaceItem,
    goTo: goTo,
    setMuted: setMuted,
    isMuted: function () { return state.muted; },
    seek: seek,
    pauseAll: pauseAll,
    resume: resume,
    activeIndex: function () { return state.active; },
    count: function () { return state.slides.length; }
  };

})(window);
