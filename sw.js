const CACHE = 'rm-cache-v8';
const URLS = ['/', 'index.html', 'manifest.json', 'fuentes_data.js', 'photos_data.js', 'access_data.js', 'pueblos_andalucia.js', 'icon-192.png', 'icon-512.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(URLS)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => clients.claim())); });
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  if (url.origin !== self.location.origin) return;
  e.respondWith(
    fetch(e.request).then(nr => {
      if (nr && nr.ok) { const clone = nr.clone(); caches.open(CACHE).then(c => c.put(e.request, clone)); }
      return nr;
    }).catch(async () => {
      if (e.request.mode === 'navigate') return (await caches.match('index.html')) || new Response('Offline', { status: 503 });
      return (await caches.match(e.request, { ignoreSearch: true })) || new Response('Offline', { status: 503 });
    })
  );
});
