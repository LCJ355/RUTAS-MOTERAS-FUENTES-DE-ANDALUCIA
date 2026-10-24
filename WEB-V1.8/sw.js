/* Rutas Moteras WEB-V1.8: shell y datos en caché; APIs y teselas siempre online. */
const CACHE = 'rutas-moteras-v1.8-3';
const CORE = [
  './',
  './index.html',
  './manifest.json',
  './native.css',
  './native.js',
  './vendor/leaflet.css',
  './vendor/leaflet.js',
  './vendor/MarkerCluster.css',
  './vendor/MarkerCluster.Default.css',
  './vendor/leaflet.markercluster.js',
  './fuentes_data.js',
  './photos_data.js',
  './access_data.js',
  './municipios_andalucia.js',
  './postal_andalucia.js',
  './pueblos_andalucia.js',
  './icon-192.png',
  './icon-512.png',
  './assets/hd_logo.png',
  './assets/brujula_needle.png'
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  if (req.mode === 'navigate') {
    event.respondWith(fetch(req).catch(() => caches.match('./index.html')));
    return;
  }
  event.respondWith((async () => {
    const cached = await caches.match(req);
    if (cached) return cached;
    try {
      const res = await fetch(req);
      if (res.ok) {
        const copy = res.clone();
        caches.open(CACHE).then(c => c.put(req, copy)).catch(() => {});
      }
      return res;
    } catch (e) {
      return cached || Response.error();
    }
  })());
});
