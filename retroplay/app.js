/* ============================================================
   RetroPlay — app.js
   Library management, ROM storage (IndexedDB), console detection,
   settings and emulator launch. The actual emulation is done by
   EmulatorJS (libretro WASM cores) loaded inside player.html.
   ============================================================ */
(() => {
  'use strict';

  // ---------- Console metadata ----------
  const CONSOLES = {
    nes:    { name: 'NES',      icon: '🕹️', exts: ['nes'] },
    snes:   { name: 'SNES',     icon: '🎮', exts: ['smc', 'sfc', 'fig', 'swc'] },
    gb:     { name: 'Game Boy', icon: '👾', exts: ['gb', 'gbc'] },
    gba:    { name: 'GBA',      icon: '🎯', exts: ['gba'] },
    segaMD: { name: 'Genesis',  icon: '💠', exts: ['md', 'gen', 'smd', '68k'] },
  };
  // Ambiguous extensions require the user to pick a system.
  const AMBIGUOUS_EXTS = ['bin', 'zip', '7z'];

  const DEFAULT_SETTINGS = {
    shader: 'disabled',
    crtOverlay: false,
    autopause: true,
    smoothing: false,
    cdn: 'https://cdn.emulatorjs.org/stable/data/',
  };

  // ---------- Tiny helpers ----------
  const $  = (sel) => document.querySelector(sel);
  const $$ = (sel) => Array.from(document.querySelectorAll(sel));
  const extOf = (name) => (name.split('.').pop() || '').toLowerCase();

  function detectConsole(filename) {
    const ext = extOf(filename);
    if (AMBIGUOUS_EXTS.includes(ext)) return null;
    for (const [key, meta] of Object.entries(CONSOLES)) {
      if (meta.exts.includes(ext)) return key;
    }
    return null;
  }

  // Stable id from name + size so save states persist across sessions.
  function makeId(name, size) {
    let h = 5381;
    const s = name + '|' + size;
    for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0;
    return 'rp_' + (h >>> 0).toString(36);
  }

  function toast(msg, ms = 2200) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.remove('hidden');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => t.classList.add('hidden'), ms);
  }

  // ---------- Settings (localStorage) ----------
  const Settings = {
    load() {
      try {
        return Object.assign({}, DEFAULT_SETTINGS, JSON.parse(localStorage.getItem('rp_settings') || '{}'));
      } catch { return { ...DEFAULT_SETTINGS }; }
    },
    save(s) { localStorage.setItem('rp_settings', JSON.stringify(s)); },
  };
  let settings = Settings.load();

  // ---------- IndexedDB ROM store ----------
  const DB = {
    _db: null,
    open() {
      return new Promise((resolve, reject) => {
        if (this._db) return resolve(this._db);
        const req = indexedDB.open('retroplay', 1);
        req.onupgradeneeded = (e) => {
          const db = e.target.result;
          if (!db.objectStoreNames.contains('roms')) {
            db.createObjectStore('roms', { keyPath: 'id' });
          }
        };
        req.onsuccess = () => { this._db = req.result; resolve(this._db); };
        req.onerror = () => reject(req.error);
      });
    },
    async _tx(mode) {
      const db = await this.open();
      return db.transaction('roms', mode).objectStore('roms');
    },
    async put(rom) {
      const store = await this._tx('readwrite');
      return new Promise((res, rej) => {
        const r = store.put(rom); r.onsuccess = () => res(); r.onerror = () => rej(r.error);
      });
    },
    async all() {
      const store = await this._tx('readonly');
      return new Promise((res, rej) => {
        const r = store.getAll(); r.onsuccess = () => res(r.result || []); r.onerror = () => rej(r.error);
      });
    },
    async get(id) {
      const store = await this._tx('readonly');
      return new Promise((res, rej) => {
        const r = store.get(id); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error);
      });
    },
    async del(id) {
      const store = await this._tx('readwrite');
      return new Promise((res, rej) => {
        const r = store.delete(id); r.onsuccess = () => res(); r.onerror = () => rej(r.error);
      });
    },
  };

  // ---------- Library rendering ----------
  let currentFilter = 'all';

  function prettyTitle(filename) {
    return filename
      .replace(/\.[^.]+$/, '')
      .replace(/[_.]+/g, ' ')
      .replace(/\((?:U|E|J|USA|Europe|Japan|!)\)/gi, '')
      .replace(/\[[^\]]*\]/g, '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  async function renderLibrary() {
    const roms = (await DB.all()).sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0));
    const lib = $('#library');
    const empty = $('#empty-state');

    const filtered = currentFilter === 'all' ? roms : roms.filter(r => r.console === currentFilter);

    if (roms.length === 0) {
      lib.innerHTML = '';
      empty.classList.remove('hidden');
      return;
    }
    empty.classList.add('hidden');

    lib.innerHTML = filtered.map(rom => {
      const meta = CONSOLES[rom.console] || { name: rom.console, icon: '🎮' };
      const kb = Math.round((rom.size || 0) / 1024);
      return `
        <div class="card" data-id="${rom.id}" role="button" tabindex="0">
          <button class="card-del" data-del="${rom.id}" title="Remove">✕</button>
          <div class="card-art">${meta.icon}</div>
          <span class="card-badge">${meta.name}</span>
          <div class="card-info">
            <div class="card-title">${escapeHtml(rom.title)}</div>
            <div class="card-meta">${kb} KB</div>
          </div>
        </div>`;
    }).join('') || `<p class="muted" style="grid-column:1/-1;padding:20px;text-align:center;">No ${CONSOLES[currentFilter]?.name || ''} games yet.</p>`;
  }

  function escapeHtml(s) {
    return s.replace(/[&<>"']/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));
  }

  // ---------- Adding ROMs ----------
  let pendingFile = null; // holds a File awaiting console selection

  async function handleFiles(fileList) {
    const files = Array.from(fileList);
    for (const file of files) {
      const con = detectConsole(file.name);
      if (con) {
        await storeRom(file, con);
      } else {
        // Ambiguous — ask the user which console.
        pendingFile = file;
        $('#picker-filename').textContent = file.name;
        $('#picker-modal').classList.remove('hidden');
        return; // handle one ambiguous file at a time
      }
    }
    await renderLibrary();
  }

  async function storeRom(file, con) {
    try {
      const buf = await file.arrayBuffer();
      const id = makeId(file.name, file.size);
      const rom = {
        id,
        filename: file.name,
        title: prettyTitle(file.name),
        console: con,
        size: file.size,
        data: buf,          // ArrayBuffer stored directly in IndexedDB
        added: Date.now(),
        lastPlayed: 0,
      };
      await DB.put(rom);
      toast(`Added “${rom.title}”`);
    } catch (err) {
      console.error(err);
      toast('Could not read that file.');
    }
  }

  // ---------- Launching the emulator ----------
  let activeBlobUrl = null;

  async function play(id) {
    const rom = await DB.get(id);
    if (!rom) { toast('Game not found.'); return; }

    rom.lastPlayed = Date.now();
    DB.put(rom).catch(() => {});

    // Build a blob URL for the ROM data (same-origin, readable by the iframe).
    if (activeBlobUrl) URL.revokeObjectURL(activeBlobUrl);
    const blob = new Blob([rom.data], { type: 'application/octet-stream' });
    activeBlobUrl = URL.createObjectURL(blob);

    const params = new URLSearchParams({
      core: rom.console,
      rom: activeBlobUrl,
      name: rom.title,
      id: rom.id,
      ext: extOf(rom.filename),
      shader: settings.shader,
      smoothing: settings.smoothing ? '1' : '0',
      cdn: settings.cdn,
    });

    const frame = $('#emu-frame');
    frame.src = 'player.html?' + params.toString();

    $('#now-playing').textContent = rom.title;
    $('#player').classList.remove('hidden');

    // CRT overlay default
    $('#crt-overlay').classList.toggle('hidden', !settings.crtOverlay);
    $('#btn-crt').style.opacity = settings.crtOverlay ? '1' : '.6';

    document.body.style.overflow = 'hidden';
    tryLockLandscapeHint();
  }

  function stopPlaying() {
    const frame = $('#emu-frame');
    frame.src = 'about:blank';           // tears down EmulatorJS + WASM
    if (activeBlobUrl) { URL.revokeObjectURL(activeBlobUrl); activeBlobUrl = null; }
    $('#player').classList.add('hidden');
    document.body.style.overflow = '';
    renderLibrary();
  }

  // Post a command into the emulator iframe.
  function emuCommand(action, value) {
    const frame = $('#emu-frame');
    if (frame && frame.contentWindow) {
      frame.contentWindow.postMessage({ __retroplay: true, action, value }, '*');
    }
  }

  function tryLockLandscapeHint() {
    // Best-effort; ignored on browsers that disallow it. Player still works portrait.
    try { if (screen.orientation && screen.orientation.lock) screen.orientation.lock('landscape').catch(() => {}); }
    catch { /* no-op */ }
  }

  // ---------- Auto-pause on background ----------
  document.addEventListener('visibilitychange', () => {
    if (!settings.autopause) return;
    if ($('#player').classList.contains('hidden')) return;
    emuCommand(document.hidden ? 'pause' : 'resume');
  });

  // ---------- Settings modal wiring ----------
  function openSettings() {
    $('#setting-shader').value      = settings.shader;
    $('#setting-crt-overlay').checked = settings.crtOverlay;
    $('#setting-autopause').checked = settings.autopause;
    $('#setting-smoothing').checked = settings.smoothing;
    $('#setting-cdn').value         = settings.cdn;
    $('#settings-modal').classList.remove('hidden');
  }
  function closeSettings() {
    settings = {
      shader:     $('#setting-shader').value,
      crtOverlay: $('#setting-crt-overlay').checked,
      autopause:  $('#setting-autopause').checked,
      smoothing:  $('#setting-smoothing').checked,
      cdn:        ($('#setting-cdn').value || DEFAULT_SETTINGS.cdn).trim(),
    };
    Settings.save(settings);
    $('#settings-modal').classList.add('hidden');
  }

  // ---------- Event wiring ----------
  function wire() {
    // Add game
    const fileInput = $('#file-input');
    const openPicker = () => fileInput.click();
    $('#btn-add').addEventListener('click', openPicker);
    $('#empty-add').addEventListener('click', openPicker);
    fileInput.addEventListener('change', (e) => {
      if (e.target.files.length) handleFiles(e.target.files);
      e.target.value = '';
    });

    // Library interactions (event delegation)
    $('#library').addEventListener('click', (e) => {
      const del = e.target.closest('[data-del]');
      if (del) {
        e.stopPropagation();
        const id = del.getAttribute('data-del');
        if (confirm('Remove this game from your library?')) {
          DB.del(id).then(renderLibrary);
        }
        return;
      }
      const card = e.target.closest('.card');
      if (card) play(card.getAttribute('data-id'));
    });

    // Console filter chips
    $('#console-filter').addEventListener('click', (e) => {
      const chip = e.target.closest('.chip');
      if (!chip) return;
      $$('.chip').forEach(c => c.classList.remove('active'));
      chip.classList.add('active');
      currentFilter = chip.getAttribute('data-console');
      renderLibrary();
    });

    // Console picker (ambiguous files)
    $('.picker-grid').addEventListener('click', async (e) => {
      const opt = e.target.closest('.picker-opt');
      if (!opt || !pendingFile) return;
      const con = opt.getAttribute('data-console');
      $('#picker-modal').classList.add('hidden');
      await storeRom(pendingFile, con);
      pendingFile = null;
      renderLibrary();
    });
    $('#picker-cancel').addEventListener('click', () => {
      pendingFile = null;
      $('#picker-modal').classList.add('hidden');
    });

    // Settings
    $('#btn-settings').addEventListener('click', openSettings);
    $('#settings-close').addEventListener('click', closeSettings);

    // Player controls
    $('#btn-back').addEventListener('click', stopPlaying);
    $('#btn-crt').addEventListener('click', () => {
      const ov = $('#crt-overlay');
      const on = ov.classList.toggle('hidden');
      $('#btn-crt').style.opacity = on ? '.6' : '1';
    });
    $('#btn-fs').addEventListener('click', () => {
      const el = $('#player');
      if (!document.fullscreenElement) (el.requestFullscreen || el.webkitRequestFullscreen)?.call(el);
      else document.exitFullscreen?.();
    });

    // Hardware back button (Android WebView) — leave the player instead of exiting the app.
    window.addEventListener('popstate', () => {
      if (!$('#player').classList.contains('hidden')) stopPlaying();
    });

    // Messages from the emulator iframe (status, errors).
    window.addEventListener('message', (e) => {
      const d = e.data || {};
      if (!d.__retroplay_frame) return;
      if (d.type === 'error') toast(d.message || 'Emulator error.');
      if (d.type === 'ready') { /* core loaded */ }
    });

    // Close modals by tapping the backdrop.
    $$('.modal').forEach(m => m.addEventListener('click', (e) => {
      if (e.target === m) m.classList.add('hidden');
    }));
  }

  // ---------- Init ----------
  async function init() {
    wire();
    try { await DB.open(); } catch (e) { toast('Storage unavailable — private mode?'); }
    await renderLibrary();
    // Push a history state so the Android back button can be intercepted for the player.
    history.replaceState({ view: 'library' }, '');
  }

  document.addEventListener('DOMContentLoaded', init);
})();
