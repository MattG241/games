/* ============================================================
   TokFeed — app shell
   ------------------------------------------------------------
   Input screen, persistence, and the wiring between the
   resolvers and the feed.
   ============================================================ */
(function (global) {
  'use strict';

  var TF = global.TF;
  var R = TF.resolvers;

  var KEY_STATE = 'tokfeed.state.v1';
  var KEY_OPTS = 'tokfeed.settings.v1';
  var KEY_LISTS = 'tokfeed.lists.v1';

  // Extracted CDN links are signed and time-limited; after this we quietly
  // re-resolve rather than handing the player a dead URL.
  var STALE_MS = 75 * 60 * 1000;

  var $ = function (id) { return document.getElementById(id); };

  var ui = {};
  var settings = { preferHd: true, autoAdvance: false, customResolver: '' };
  var current = { links: [], items: [] };
  var deferredInstall = null;

  /* ---------------- storage ---------------- */

  function read(key, fallback) {
    try {
      var raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (e) {
      return fallback;
    }
  }

  function write(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch (e) {
      // Quota or private mode — the app still works, it just won't remember.
      return false;
    }
  }

  function saveState() {
    // Object URLs for local files are per-session, so they're stripped before
    // saving — showFeed() re-creates them from IndexedDB on the way in.
    var items = current.items.map(function (item) {
      if (item && item.local) {
        return { kind: 'video', local: true, fileId: item.fileId, title: item.title,
                 size: item.size, provider: 'local' };
      }
      return item;
    });
    write(KEY_STATE, { links: current.links, items: items, savedAt: Date.now() });
  }

  /* ---------------- local video files (IndexedDB) ---------------- */

  // Videos are far too big for localStorage, so the blobs live in IndexedDB
  // and only their metadata is mirrored into the saved state.
  var DB_NAME = 'tokfeed';
  var DB_STORE = 'files';

  function openDB() {
    return new Promise(function (resolve, reject) {
      if (!global.indexedDB) { reject(new Error('This browser has no IndexedDB')); return; }
      var req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = function () {
        var db = req.result;
        if (!db.objectStoreNames.contains(DB_STORE)) {
          db.createObjectStore(DB_STORE, { keyPath: 'id' });
        }
      };
      req.onsuccess = function () { resolve(req.result); };
      req.onerror = function () { reject(req.error || new Error('IndexedDB blocked')); };
    });
  }

  function dbRun(mode, fn) {
    return openDB().then(function (db) {
      return new Promise(function (resolve, reject) {
        var tx = db.transaction(DB_STORE, mode);
        var req = fn(tx.objectStore(DB_STORE));
        tx.oncomplete = function () { db.close(); resolve(req && req.result); };
        tx.onerror = function () { db.close(); reject(tx.error); };
        tx.onabort = function () { db.close(); reject(tx.error || new Error('Storage full')); };
      });
    });
  }

  function dbPut(rec) { return dbRun('readwrite', function (s) { return s.put(rec); }); }
  function dbGet(id) { return dbRun('readonly', function (s) { return s.get(id); }); }
  function dbDelete(id) { return dbRun('readwrite', function (s) { return s.delete(id); }); }
  function dbClear() { return dbRun('readwrite', function (s) { return s.clear(); }); }

  function formatSize(bytes) {
    if (!bytes) return '';
    var mb = bytes / (1024 * 1024);
    return mb >= 1024 ? (mb / 1024).toFixed(1) + ' GB' : mb.toFixed(mb < 10 ? 1 : 0) + ' MB';
  }

  var objectUrls = [];

  function releaseObjectUrls() {
    objectUrls.forEach(function (u) {
      try { URL.revokeObjectURL(u); } catch (e) {}
    });
    objectUrls = [];
    current.items.forEach(function (item) {
      if (item && item.local) item.url = '';
    });
  }

  /** Turn every stored local file back into a playable object URL. */
  function hydrateLocalItems() {
    var jobs = current.items.map(function (item, i) {
      if (!item || !item.local || item.url) return Promise.resolve();
      return dbGet(item.fileId).then(function (rec) {
        if (!rec || !rec.blob) {
          current.items[i] = { kind: 'error', title: item.title,
                               error: 'This video is no longer stored on the phone.' };
          return;
        }
        var url = URL.createObjectURL(rec.blob);
        objectUrls.push(url);
        item.url = url;
        item.alt = [];
      }).catch(function () {
        current.items[i] = { kind: 'error', title: item.title,
                             error: 'Couldn\'t read this video from storage.' };
      });
    });
    return Promise.all(jobs);
  }

  function addFiles(fileList) {
    var files = Array.prototype.slice.call(fileList || []).filter(function (f) {
      return /^video\//.test(f.type) || /\.(mp4|m4v|webm|mov|mkv|ogv)$/i.test(f.name);
    });
    if (!files.length) {
      toastInput('Those don\'t look like video files.');
      return Promise.resolve();
    }

    var added = 0;
    var chain = Promise.resolve();

    files.forEach(function (file) {
      chain = chain.then(function () {
        var id = 'f' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
        return dbPut({ id: id, name: file.name, type: file.type, size: file.size, blob: file })
          .then(function () {
            current.items.push({
              kind: 'video', local: true, fileId: id,
              title: file.name, size: file.size, provider: 'local', resolvedAt: Date.now()
            });
            added++;
          })
          .catch(function (err) {
            toastInput('Couldn\'t store "' + file.name + '": ' +
                       ((err && err.message) || 'storage full'));
          });
      });
    });

    return chain.then(function () {
      if (!added) return;
      saveState();
      renderFiles();
      ui.statusCard.hidden = false;
      ui.statusList.innerHTML = '';
      ui.statusTitle.textContent = added + ' video' + (added > 1 ? 's' : '') +
        ' added — ' + current.items.length + ' in the feed';
      ui.btnOpenFeed.hidden = false;
    });
  }

  function removeFile(fileId) {
    dbDelete(fileId).catch(function () {}).then(function () {
      current.items = current.items.filter(function (item) {
        return !(item && item.local && item.fileId === fileId);
      });
      saveState();
      renderFiles();
    });
  }

  function renderFiles() {
    var locals = current.items.filter(function (i) { return i && i.local; });
    ui.filesCard.hidden = !locals.length;
    ui.filesList.innerHTML = '';
    if (!locals.length) return;

    var total = locals.reduce(function (n, i) { return n + (i.size || 0); }, 0);
    ui.filesSize.textContent = locals.length + ' file' + (locals.length > 1 ? 's' : '') +
      (total ? ' · ' + formatSize(total) : '');

    locals.forEach(function (item) {
      var li = document.createElement('li');

      var icon = document.createElement('span');
      icon.innerHTML = '<svg viewBox="0 0 24 24" class="ico" aria-hidden="true" fill="none" ' +
        'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
        '<rect x="2" y="4" width="14" height="16" rx="2"/><path d="M22 8l-6 4 6 4z"/></svg>';

      var name = document.createElement('span');
      name.className = 'file-name';
      name.textContent = item.title;

      var size = document.createElement('span');
      size.className = 'file-size';
      size.textContent = formatSize(item.size);

      var del = document.createElement('button');
      del.className = 'chip-x';
      del.textContent = '×';
      del.setAttribute('aria-label', 'Remove ' + item.title);
      del.addEventListener('click', function () { removeFile(item.fileId); });

      li.appendChild(icon.firstChild);
      li.appendChild(name);
      li.appendChild(size);
      li.appendChild(del);
      ui.filesList.appendChild(li);
    });
  }

  function saveSettings() {
    write(KEY_OPTS, settings);
  }

  /* ---------------- toast ---------------- */

  var toastTimer = 0;
  function toast(msg) {
    if (!ui.toast) return;
    ui.toast.textContent = msg;
    ui.toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { ui.toast.hidden = true; }, 2600);
  }

  /* ---------------- views ---------------- */

  function showFeed(startIndex) {
    if (!current.items.length) return Promise.resolve();

    // Opening the feed is a fresh start: give every slide its retry budget back.
    autoRetries = Object.create(null);
    refreshing = Object.create(null);

    // Local files need a fresh object URL before the feed can play them.
    return hydrateLocalItems().then(function () { mountFeed(startIndex); });
  }

  function mountFeed(startIndex) {
    ui.viewInput.classList.remove('is-active');
    ui.viewFeed.classList.add('is-active');
    ui.viewFeed.setAttribute('aria-hidden', 'false');
    ui.viewInput.setAttribute('aria-hidden', 'true');

    TF.feed.mount(ui.feed, current.items, {
      muted: true,
      autoAdvance: settings.autoAdvance,
      startIndex: startIndex || 0,
      hooks: {
        onIndex: function (i, n) { ui.counter.textContent = (i + 1) + ' / ' + n; },
        onProgress: function (f) {
          ui.progressFill.style.width = (f * 100).toFixed(2) + '%';
          ui.progress.setAttribute('aria-valuenow', Math.round(f * 100));
        },
        onProgressVisible: function (visible) {
          ui.progress.classList.toggle('is-hidden', !visible);
        },
        onMute: function (muted) {
          ui.btnMute.setAttribute('aria-pressed', muted ? 'true' : 'false');
          ui.btnMute.setAttribute('aria-label', muted ? 'Unmute' : 'Mute');
        },
        onRefresh: function (i) { refreshItem(i, 'error'); },
        onRetry: function (i) { refreshItem(i, 'user'); }
      }
    });

    ui.btnMute.setAttribute('aria-pressed', 'true');

    // Give the Android back button something to pop, so it returns to the
    // input screen instead of closing the app.
    if (global.history && history.pushState) {
      history.pushState({ tf: 'feed' }, '', '#feed');
    }

    refreshStale();
  }

  function showInput(fromPop) {
    TF.feed.unmount();
    releaseObjectUrls();
    ui.viewFeed.classList.remove('is-active');
    ui.viewInput.classList.add('is-active');
    ui.viewFeed.setAttribute('aria-hidden', 'true');
    ui.viewInput.setAttribute('aria-hidden', 'false');

    if (!fromPop && global.history && history.state && history.state.tf === 'feed') {
      history.back();
    }
  }

  function feedIsOpen() {
    return ui.viewFeed.classList.contains('is-active');
  }

  /* ---------------- extraction ---------------- */

  function statusRow(url) {
    var li = document.createElement('li');
    var dot = document.createElement('span');
    dot.className = 'dot pending';
    var text = document.createElement('span');
    text.className = 'status-text';

    var u = document.createElement('span');
    u.className = 'status-url';
    u.textContent = url;
    var note = document.createElement('span');
    note.className = 'status-note';
    note.textContent = 'Extracting…';

    text.appendChild(u);
    text.appendChild(note);
    li.appendChild(dot);
    li.appendChild(text);
    ui.statusList.appendChild(li);

    return {
      ok: function (item) {
        dot.className = 'dot ok';
        note.className = 'status-note';
        var bits = [];
        if (item.author) bits.push(item.author);
        bits.push(item.kind === 'embed' ? 'embedded player' : 'direct video');
        if (item.duration) bits.push(item.duration + 's');
        note.textContent = bits.join(' · ');
      },
      fail: function (message) {
        dot.className = 'dot fail';
        note.className = 'status-note err';
        note.textContent = message;
      }
    };
  }

  /** Run `worker` over `list` with a small concurrency window. */
  function pool(list, limit, worker) {
    var i = 0;
    function next() {
      if (i >= list.length) return Promise.resolve();
      var idx = i++;
      return worker(list[idx], idx).then(next);
    }
    var runners = [];
    for (var k = 0; k < Math.min(limit, list.length); k++) runners.push(next());
    return Promise.all(runners);
  }

  function extract() {
    var links = R.parseLinks(ui.links.value);
    var locals = current.items.filter(function (i) { return i && i.local; });

    if (!links.length) {
      // Files on their own are a perfectly good feed — no extraction needed.
      if (locals.length) {
        current.links = [];
        current.items = locals;
        saveState();
        return showFeed(0);
      }
      toastInput('Paste a link or add a video from this phone first.');
      return;
    }

    ui.btnLoad.classList.add('is-busy');
    ui.btnLoad.disabled = true;
    ui.statusCard.hidden = false;
    ui.statusList.innerHTML = '';
    ui.btnOpenFeed.hidden = true;
    ui.statusTitle.textContent = 'Extracting ' + links.length + ' link' + (links.length > 1 ? 's' : '') + '…';

    var rows = links.map(statusRow);
    var results = new Array(links.length);
    var failures = 0;

    var opts = { preferHd: settings.preferHd, customResolver: settings.customResolver };

    return pool(links, 3, function (url, idx) {
      return R.resolve(url, opts).then(function (item) {
        results[idx] = item;
        rows[idx].ok(item);
      }).catch(function (err) {
        failures++;
        var msg = (err && err.message) || 'Extraction failed';
        results[idx] = { src: url, host: R.hostOf(url), error: msg, kind: 'error' };
        rows[idx].fail(msg);
      });
    }).then(function () {
      ui.btnLoad.classList.remove('is-busy');
      ui.btnLoad.disabled = false;

      current.links = links;
      // Extracted links keep their pasted order; uploaded files follow them.
      current.items = results.concat(locals);
      saveState();

      var ok = links.length - failures;
      ui.statusTitle.textContent = ok + ' of ' + links.length + ' ready' +
        (locals.length ? ' · ' + locals.length + ' from this phone' : '');

      if (!ok && !locals.length) {
        ui.statusTitle.textContent = 'Nothing could be extracted';
        return;
      }

      ui.btnOpenFeed.hidden = false;
      // Clean run: go straight to the feed, that's what the button promised.
      if (!failures) return showFeed(0);
    });
  }

  // A re-resolve produces a brand new item object, so any "already tried"
  // flag stored on the item itself would be thrown away — and a link that is
  // permanently broken would re-extract forever. Both counters are therefore
  // keyed by slide index and live outside the items.
  var autoRetries = Object.create(null);
  var refreshing = Object.create(null);
  var MAX_AUTO_RETRIES = 1;

  function showDead(index, message) {
    var item = current.items[index] || {};
    var failed = { src: item.src, host: item.host, error: message, kind: 'error' };
    current.items[index] = failed;
    TF.feed.replaceItem(index, failed);
  }

  /**
   * Re-resolve a single item.
   * @param {number} index
   * @param {'error'|'stale'|'user'} reason  'user' = tapped Retry, and resets
   *        the automatic-retry budget for that slide.
   */
  function refreshItem(index, reason) {
    var item = current.items[index];
    if (!item) return;

    // A local file has nothing to re-extract — if it won't play, the phone
    // can't decode it.
    if (item.local) {
      showDead(index, 'This phone can\'t play "' + (item.title || 'that file') + '".');
      return;
    }

    if (!item.src) return;
    if (refreshing[index]) return;

    if (reason === 'user') {
      autoRetries[index] = 0;
    } else if (reason === 'error') {
      if ((autoRetries[index] || 0) >= MAX_AUTO_RETRIES) {
        showDead(index, 'This link still won\'t play. Tap Retry or open the original.');
        return;
      }
      autoRetries[index] = (autoRetries[index] || 0) + 1;
    }

    refreshing[index] = true;
    if (reason === 'user') toast('Re-extracting…');

    R.resolve(item.src, { preferHd: settings.preferHd, customResolver: settings.customResolver })
      .then(function (fresh) {
        refreshing[index] = false;
        current.items[index] = fresh;
        saveState();
        TF.feed.replaceItem(index, fresh);
        if (reason === 'user') toast('Reloaded');
      })
      .catch(function (err) {
        refreshing[index] = false;
        var msg = (err && err.message) || 'Extraction failed';
        showDead(index, msg);
        if (reason === 'user') toast(msg);
      });
  }

  /** Quietly refresh anything whose CDN link is probably expired. */
  function refreshStale() {
    var now = Date.now();
    current.items.forEach(function (item, i) {
      if (!item || item.kind !== 'video') return;
      if (item.local) return;                          // stored on the phone, never expires
      if (item.provider === 'direct') return;          // user's own link, not time-limited
      if (item.resolvedAt && now - item.resolvedAt < STALE_MS) return;
      refreshItem(i, 'stale');
    });
  }

  /* ---------------- saved lists ---------------- */

  function renderSaved() {
    var lists = read(KEY_LISTS, []);
    ui.savedCard.hidden = !lists.length;
    ui.savedChips.innerHTML = '';

    lists.forEach(function (list, i) {
      var chip = document.createElement('span');
      chip.className = 'chip';

      var label = document.createElement('b');
      label.textContent = list.name;
      var count = document.createElement('small');
      count.textContent = list.links.length + ' link' + (list.links.length > 1 ? 's' : '');

      var open = document.createElement('button');
      open.className = 'chip-x';
      open.setAttribute('aria-label', 'Remove ' + list.name);
      open.textContent = '×';
      open.addEventListener('click', function (e) {
        e.stopPropagation();
        var all = read(KEY_LISTS, []);
        all.splice(i, 1);
        write(KEY_LISTS, all);
        renderSaved();
      });

      chip.appendChild(label);
      chip.appendChild(count);
      chip.appendChild(open);
      chip.addEventListener('click', function () {
        ui.links.value = list.links.join('\n');
        toastInput('Loaded "' + list.name + '" — tap Extract & Load');
        ui.links.scrollIntoView({ behavior: 'smooth', block: 'center' });
      });

      ui.savedChips.appendChild(chip);
    });
  }

  function saveList() {
    var links = R.parseLinks(ui.links.value);
    if (!links.length) { toastInput('Nothing to save.'); return; }

    var name = prompt('Name this list', 'List ' + (read(KEY_LISTS, []).length + 1));
    if (!name) return;

    var lists = read(KEY_LISTS, []);
    lists.unshift({ name: String(name).slice(0, 40), links: links, savedAt: Date.now() });
    write(KEY_LISTS, lists.slice(0, 20));
    renderSaved();
  }

  // On the input screen there's no toast layer, so reuse the status card.
  function toastInput(msg) {
    ui.statusCard.hidden = false;
    ui.statusList.innerHTML = '';
    ui.btnOpenFeed.hidden = current.items.length ? false : true;
    ui.statusTitle.textContent = msg;
  }

  /* ---------------- progress bar scrubbing ---------------- */

  function bindScrub() {
    var dragging = false;

    function fractionFrom(e) {
      var track = ui.progress.querySelector('.progress-track');
      var box = track.getBoundingClientRect();
      var x = (e.touches ? e.touches[0].clientX : e.clientX) - box.left;
      return box.width ? x / box.width : 0;
    }

    ui.progress.addEventListener('pointerdown', function (e) {
      dragging = true;
      ui.progress.classList.add('is-scrubbing');
      ui.progress.setPointerCapture(e.pointerId);
      TF.feed.seek(fractionFrom(e));
    });

    ui.progress.addEventListener('pointermove', function (e) {
      if (dragging) TF.feed.seek(fractionFrom(e));
    });

    function end(e) {
      if (!dragging) return;
      dragging = false;
      ui.progress.classList.remove('is-scrubbing');
      try { ui.progress.releasePointerCapture(e.pointerId); } catch (err) {}
    }
    ui.progress.addEventListener('pointerup', end);
    ui.progress.addEventListener('pointercancel', end);
  }

  /* ---------------- share target ---------------- */

  /**
   * Installed as a PWA, TokFeed registers as an Android share target, so
   * "Share → TokFeed" from the TikTok app lands here as ?text=/?url=.
   */
  function consumeShare() {
    var q = new URLSearchParams(location.search);
    var shared = [q.get('url'), q.get('text'), q.get('title')].filter(Boolean).join('\n');
    if (!shared) return false;

    var links = R.parseLinks(shared);
    if (!links.length) return false;

    // Append rather than replace, so several shares can stack up.
    var existing = R.parseLinks(ui.links.value);
    var merged = existing.concat(links.filter(function (l) { return existing.indexOf(l) === -1; }));
    ui.links.value = merged.join('\n');

    // Drop the query string so a reload doesn't re-add the same link.
    if (history.replaceState) {
      history.replaceState(null, '', location.pathname);
    }
    return true;
  }

  /* ---------------- service worker + install ---------------- */

  function registerSW() {
    if (!('serviceWorker' in navigator)) return;
    if (location.protocol !== 'https:' && location.hostname !== 'localhost') return;
    navigator.serviceWorker.register('./sw.js').catch(function () { /* not fatal */ });
  }

  function bindInstall() {
    global.addEventListener('beforeinstallprompt', function (e) {
      e.preventDefault();
      deferredInstall = e;
      ui.btnInstall.hidden = false;
    });

    ui.btnInstall.addEventListener('click', function () {
      if (!deferredInstall) return;
      deferredInstall.prompt();
      deferredInstall = null;
      ui.btnInstall.hidden = true;
    });

    // iOS has no install prompt event — tell the user where the button is.
    var isStandalone = global.matchMedia('(display-mode: standalone)').matches || navigator.standalone;
    var isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent);
    if (isIOS && !isStandalone) ui.installHint.hidden = false;
  }

  /* ---------------- boot ---------------- */

  function bind() {
    ui.btnLoad.addEventListener('click', extract);

    ui.btnPaste.addEventListener('click', function () {
      if (!navigator.clipboard || !navigator.clipboard.readText) {
        toastInput('Clipboard access needs HTTPS — paste with a long-press instead.');
        return;
      }
      navigator.clipboard.readText().then(function (text) {
        var links = R.parseLinks(text);
        if (!links.length) { toastInput('No link found on the clipboard.'); return; }
        var existing = R.parseLinks(ui.links.value);
        var add = links.filter(function (l) { return existing.indexOf(l) === -1; });
        ui.links.value = existing.concat(add).join('\n');
        if (!add.length) toastInput('Already in the list.');
      }).catch(function () {
        toastInput('Clipboard blocked — paste with a long-press instead.');
      });
    });

    ui.btnClear.addEventListener('click', function () {
      ui.links.value = '';
      ui.statusCard.hidden = true;
      ui.links.focus();
    });

    ui.btnPick.addEventListener('click', function () { ui.fileInput.click(); });

    ui.fileInput.addEventListener('change', function () {
      addFiles(ui.fileInput.files).then(function () {
        ui.fileInput.value = '';       // re-picking the same file must still fire
      });
    });

    ui.btnOpenFeed.addEventListener('click', function () { showFeed(0); });
    ui.btnBack.addEventListener('click', function () { showInput(false); });

    ui.btnMute.addEventListener('click', function () {
      TF.feed.setMuted(!TF.feed.isMuted());
    });

    ui.optHd.addEventListener('change', function () {
      settings.preferHd = ui.optHd.checked;
      saveSettings();
    });
    ui.optLoop.addEventListener('change', function () {
      settings.autoAdvance = ui.optLoop.checked;
      saveSettings();
    });
    ui.optResolver.addEventListener('change', function () {
      settings.customResolver = ui.optResolver.value.trim();
      saveSettings();
    });

    ui.btnSaveList.addEventListener('click', saveList);

    ui.btnWipe.addEventListener('click', function () {
      if (!confirm('Clear the cached feed, saved lists and any videos stored on this phone?')) return;
      [KEY_STATE, KEY_LISTS].forEach(function (k) {
        try { localStorage.removeItem(k); } catch (e) {}
      });
      releaseObjectUrls();
      dbClear().catch(function () {}).then(function () {
        current = { links: [], items: [] };
        ui.statusCard.hidden = true;
        renderSaved();
        renderFiles();
        toastInput('Cache cleared.');
      });
    });

    // Android/browser back button closes the feed rather than the app.
    global.addEventListener('popstate', function () {
      if (feedIsOpen()) showInput(true);
    });

    // Don't keep decoding video in a backgrounded tab.
    document.addEventListener('visibilitychange', function () {
      if (!feedIsOpen()) return;
      if (document.hidden) TF.feed.pauseAll();
      else TF.feed.resume();
    });

    document.addEventListener('keydown', function (e) {
      if (!feedIsOpen()) return;
      var i = TF.feed.activeIndex();
      if (e.key === 'ArrowDown' || e.key === 'PageDown') { TF.feed.goTo(i + 1, true); e.preventDefault(); }
      else if (e.key === 'ArrowUp' || e.key === 'PageUp') { TF.feed.goTo(i - 1, true); e.preventDefault(); }
      else if (e.key === 'm' || e.key === 'M') { TF.feed.setMuted(!TF.feed.isMuted()); }
      else if (e.key === 'Escape') { showInput(false); }
    });

    bindScrub();
  }

  function init() {
    ui = {
      viewInput: $('view-input'),
      viewFeed: $('view-feed'),
      links: $('links'),
      btnLoad: $('btn-load'),
      btnPaste: $('btn-paste'),
      btnClear: $('btn-clear'),
      btnPick: $('btn-pick'),
      fileInput: $('file-input'),
      filesCard: $('files-card'),
      filesList: $('files-list'),
      filesSize: $('files-size'),
      statusCard: $('status-card'),
      statusTitle: $('status-title'),
      statusList: $('status-list'),
      btnOpenFeed: $('btn-open-feed'),
      savedCard: $('saved-card'),
      savedChips: $('saved-chips'),
      optHd: $('opt-hd'),
      optLoop: $('opt-loop'),
      optResolver: $('opt-resolver'),
      btnSaveList: $('btn-save-list'),
      btnWipe: $('btn-wipe'),
      btnInstall: $('btn-install'),
      installHint: $('install-hint'),
      feed: $('feed'),
      counter: $('feed-counter'),
      btnBack: $('btn-back'),
      btnMute: $('btn-mute'),
      progress: $('progress'),
      progressFill: $('progress-fill'),
      toast: $('toast')
    };

    settings = Object.assign(settings, read(KEY_OPTS, {}));
    ui.optHd.checked = settings.preferHd !== false;
    ui.optLoop.checked = !!settings.autoAdvance;
    ui.optResolver.value = settings.customResolver || '';

    var saved = read(KEY_STATE, null);
    if (saved) {
      current.links = saved.links || [];
      current.items = saved.items || [];
      if (current.links.length) ui.links.value = current.links.join('\n');
      if (current.items.length) {
        ui.statusCard.hidden = false;
        ui.statusTitle.textContent = 'Last feed: ' + current.items.length + ' video' +
          (current.items.length > 1 ? 's' : '');
        ui.btnOpenFeed.hidden = false;
      }
    }

    renderFiles();
    renderSaved();
    bind();
    bindInstall();
    registerSW();

    // A share-sheet hand-off should just work: load it immediately.
    if (consumeShare()) extract();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})(window);
