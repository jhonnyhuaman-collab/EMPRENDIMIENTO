import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';

const workerSource = await readFile(new URL('../src/main/resources/static/sw.js', import.meta.url), 'utf8');
const installSource = await readFile(new URL('../src/main/resources/static/js/pwa.js', import.meta.url), 'utf8');

function worker(offline = false) {
    const handlers = {};
    const cached = new Map();
    const deleted = [];
    const network = [];
    const fallback = { body: 'public offline page' };
    const cache = {
        addAll: async paths => paths.forEach(path => cached.set(path, fallback)),
        match: async request => cached.get(typeof request === 'string' ? request : new URL(request.url).pathname)
    };
    vm.runInNewContext(workerSource, {
        URL, Response,
        fetch: async request => {
            network.push(request);
            if (offline) throw new Error('offline');
            return { body: 'private live response', status: 200 };
        },
        caches: {
            open: async () => cache,
            keys: async () => ['planificador-publico-v0', 'otra-aplicacion', 'planificador-publico-v1'],
            delete: async name => deleted.push(name)
        },
        self: {
            location: { origin: 'https://app.example' },
            addEventListener: (name, fn) => { handlers[name] = fn; },
            skipWaiting: async () => {},
            clients: { claim: async () => {} }
        }
    });
    return { handlers, cached, deleted, network, fallback };
}

async function lifecycle(handler) {
    let work;
    handler({ waitUntil: promise => { work = promise; } });
    await work;
}

function request(path, mode = 'navigate', method = 'GET') {
    return { url: new URL(path, 'https://app.example').href, mode, method };
}

function response(app, req) {
    let result;
    app.handlers.fetch({ request: req, respondWith: promise => { result = promise; } });
    return result;
}

test('solo precarga pantalla publica e iconos, nunca paginas ni API privadas', async () => {
    const app = worker();
    await lifecycle(app.handlers.install);
    assert.deepEqual([...app.cached.keys()], [
        '/offline.html', '/images/pwa/icon-192.png', '/images/pwa/icon-512.png',
        '/images/pwa/icon-maskable-512.png', '/images/pwa/apple-touch-icon.png'
    ]);
    await lifecycle(app.handlers.activate);
    assert.deepEqual(app.deleted, ['planificador-publico-v0']);
});

test('las navegaciones conectadas siempre van al servidor sin guardarse', async () => {
    const app = worker();
    await lifecycle(app.handlers.install);
    const before = [...app.cached.keys()];
    for (const path of ['/', '/agenda', '/login', '/registro', '/configuracion']) {
        assert.equal((await response(app, request(path))).body, 'private live response');
    }
    assert.equal(app.network.length, 5);
    assert.deepEqual([...app.cached.keys()], before);
});

test('sin conexion muestra solo la pantalla publica y puede cargar su icono', async () => {
    const app = worker(true);
    await lifecycle(app.handlers.install);
    assert.equal(await response(app, request('/agenda')), app.fallback);
    assert.equal(await response(app, request('/images/pwa/icon-192.png', 'no-cors')), app.fallback);
});

test('no intercepta envios, API, descargas privadas ni peticiones externas', () => {
    const app = worker(true);
    for (const req of [
        request('/login', 'navigate', 'POST'), request('/logout', 'navigate', 'POST'),
        request('/plan/generar', 'navigate', 'POST'), request('/api/eventos', 'cors'),
        request('/silabos/1', 'cors'), request('https://cdn.example/script.js', 'no-cors')
    ]) assert.equal(response(app, req), undefined);
});

function installer(standalone = false, ios = false) {
    const events = {};
    const handlers = [];
    const registrations = [];
    const buttons = [0, 1].map(() => ({
        hidden: true,
        addEventListener: (name, fn) => { if (name === 'click') handlers.push(fn); }
    }));
    vm.runInNewContext(installSource, {
        URL, console,
        document: {
            currentScript: { src: 'https://app.example/js/pwa-abc123.js' },
            querySelectorAll: () => buttons
        },
        window: {
            isSecureContext: true,
            matchMedia: () => ({ matches: standalone, addEventListener: () => {} }),
            addEventListener: (name, fn) => { events[name] = fn; }
        },
        navigator: {
            standalone: ios,
            serviceWorker: { register: async (...args) => registrations.push(args) }
        }
    });
    return { events, buttons, handlers, registrations };
}

test('oculta instalacion no soportada y registra worker estable desde JS versionado', async () => {
    const app = installer();
    assert.ok(app.buttons.every(button => button.hidden));
    await app.events.load();
    assert.equal(app.registrations[0][0], 'https://app.example/sw.js');
    assert.equal(app.registrations[0][1].updateViaCache, 'none');
});

test('ofrece instalacion cuando el navegador la permite y consume el evento una sola vez', async () => {
    const app = installer();
    let prompts = 0;
    let prevented = false;
    app.events.beforeinstallprompt({
        preventDefault: () => { prevented = true; }, prompt: async () => { prompts++; }
    });
    assert.equal(prevented, true);
    assert.ok(app.buttons.every(button => !button.hidden));
    await app.handlers[0]();
    await app.handlers[1]();
    assert.equal(prompts, 1);
    assert.ok(app.buttons.every(button => button.hidden));
});

test('no vuelve a ofrecer instalar en modo independiente de Android o iPhone', () => {
    for (const app of [installer(true), installer(false, true)]) {
        app.events.beforeinstallprompt({ preventDefault: () => {}, prompt: async () => {} });
        assert.ok(app.buttons.every(button => button.hidden));
    }
});

test('al instalar oculta todas las entradas', () => {
    const app = installer();
    app.events.beforeinstallprompt({ preventDefault: () => {}, prompt: async () => {} });
    app.events.appinstalled();
    assert.ok(app.buttons.every(button => button.hidden));
});
