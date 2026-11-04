'use strict';
const CACHE = 'planificador-netlify-public-v1';
const PUBLIC = ['/offline.html', '/offline.css', '/icons/icon-192.png', '/icons/icon-512.png', '/icons/maskable.png', '/icons/apple.png'];
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(PUBLIC)).then(() => self.skipWaiting())));
self.addEventListener('activate', event => event.waitUntil(caches.keys().then(names => Promise.all(names.filter(name => name.startsWith('planificador-netlify-public-') && name !== CACHE).map(name => caches.delete(name)))).then(() => self.clients.claim())));
self.addEventListener('fetch', event => {
  const req = event.request; const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;
  if (req.mode === 'navigate') event.respondWith(fetch(req).catch(async () => (await (await caches.open(CACHE)).match('/offline.html')) || Response.error()));
  else if (PUBLIC.includes(url.pathname)) event.respondWith(fetch(req).catch(async () => (await (await caches.open(CACHE)).match(req)) || Response.error()));
});
