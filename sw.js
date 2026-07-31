const CACHE = 'rm-cache-v3';
const URLS = ['/', 'index.html', 'manifest.json', 'fuentes_data.js', 'photos_data.js', 'pueblos_andalucia.js', 'icon-192.png', 'icon-512.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(URLS)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => clients.claim())); });
self.addEventListener('fetch', e => { e.respondWith(caches.match(e.request).then(r => { if (r) { fetch(e.request).then(nr => { if (nr && nr.ok) caches.open(CACHE).then(c => c.put(e.request, nr)); }).catch(() => {}); return r; } return fetch(e.request).then(nr => { if (nr && nr.ok) { const clone = nr.clone(); caches.open(CACHE).then(c => c.put(e.request, clone)); } return nr; }).catch(() => new Response('Offline', { status: 503 })); })); });
