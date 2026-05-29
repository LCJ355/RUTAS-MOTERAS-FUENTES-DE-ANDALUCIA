const CACHE = 'rm-cache-v1';
const URLS = ['/', 'index.html', 'manifest.json', 'fuentes_data.js', 'photos_data.js'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(URLS)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(clients.claim()); });
self.addEventListener('fetch', e => { e.respondWith(caches.match(e.request).then(r => r || fetch(e.request).catch(() => new Response('Offline', { status: 503 })))); });
