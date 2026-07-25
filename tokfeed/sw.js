/* ============================================================
   TokFeed service worker
   ------------------------------------------------------------
   Caches the app shell only, so TokFeed opens instantly and
   survives a flaky connection. Video and extraction traffic is
   never touched: those are cross-origin, time-limited URLs and
   caching them would waste storage and serve dead links.
   ============================================================ */

var CACHE = 'tokfeed-shell-v1';

var SHELL = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './feed.js',
  './resolvers.js',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/maskable-512.png',
  './icons/apple-touch-icon.png'
];

self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(CACHE)
      // addAll is all-or-nothing; add individually so one 404 can't
      // break the whole install.
      .then(function (cache) {
        return Promise.all(SHELL.map(function (url) {
          return cache.add(new Request(url, { cache: 'reload' })).catch(function () {});
        }));
      })
      .then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys()
      .then(function (keys) {
        return Promise.all(keys.map(function (k) {
          return k === CACHE ? null : caches.delete(k);
        }));
      })
      .then(function () { return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function (event) {
  var req = event.request;
  if (req.method !== 'GET') return;

  var url;
  try { url = new URL(req.url); } catch (e) { return; }

  // Leave every cross-origin request alone (video CDNs, tikwm, embeds).
  if (url.origin !== self.location.origin) return;

  // Navigations: network first so a deployed update lands immediately,
  // cache as the offline fallback.
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req).catch(function () {
        return caches.match('./index.html').then(function (hit) {
          return hit || Response.error();
        });
      })
    );
    return;
  }

  // Shell assets: cache first, refresh in the background.
  event.respondWith(
    caches.match(req).then(function (hit) {
      var net = fetch(req).then(function (res) {
        if (res && res.ok) {
          var copy = res.clone();
          caches.open(CACHE).then(function (c) { c.put(req, copy); });
        }
        return res;
      }).catch(function () {
        return hit || Response.error();
      });
      return hit || net;
    })
  );
});
