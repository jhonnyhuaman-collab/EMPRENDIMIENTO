'use strict';

const CACHE = 'planificador-publico-v2';
const OFFLINE = '/offline.html';
const PUBLIC_ASSETS = [
    OFFLINE,
    '/images/pwa/icon-192-v2.png',
    '/images/pwa/icon-512-v2.png',
    '/images/pwa/icon-maskable-512-v2.png',
    '/images/pwa/apple-touch-icon-v2.png'
];

self.addEventListener('install', event => {
    event.waitUntil(caches.open(CACHE)
        .then(cache => cache.addAll(PUBLIC_ASSETS))
        .then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
    event.waitUntil(caches.keys()
        .then(names => Promise.all(names
            .filter(name => name.startsWith('planificador-publico-') && name !== CACHE)
            .map(name => caches.delete(name))))
        .then(() => self.clients.claim()));
});

self.addEventListener('fetch', event => {
    const request = event.request;
    if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;

    // Nunca cachear sesiones, formularios, API ni paginas con datos de estudiantes.
    if (request.mode === 'navigate') {
        event.respondWith(fetch(request).catch(async () => {
            const cache = await caches.open(CACHE);
            return (await cache.match(OFFLINE)) || Response.error();
        }));
    } else if (PUBLIC_ASSETS.includes(new URL(request.url).pathname)) {
        event.respondWith(fetch(request).catch(async () => {
            const cache = await caches.open(CACHE);
            return (await cache.match(request)) || Response.error();
        }));
    }
});
