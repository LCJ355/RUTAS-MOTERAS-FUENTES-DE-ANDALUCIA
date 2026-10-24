const RETIRED_CACHE = 'rm-cache-v8';

self.addEventListener('install', event => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    await caches.delete(RETIRED_CACHE);
    await self.registration.unregister();
  })());
});
