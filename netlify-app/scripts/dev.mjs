import { createServer } from 'node:http';
import { randomBytes } from 'node:crypto';
import { mkdir } from 'node:fs/promises';
import { createServer as createVite } from 'vite';
import { createHandler } from '../server/api.mjs';
import { localDatabase } from './local-db.mjs';
import { neonDatabase } from '../server/db.mjs';

if (process.env.NETLIFY || process.env.NODE_ENV === 'production') throw new Error('Este servidor es solo para desarrollo local.');
await mkdir('.local', { recursive: true });
const db = process.env.DATABASE_URL ? neonDatabase(process.env.DATABASE_URL) : await localDatabase('.local/postgres');
const handler = createHandler({ db, env: { ...process.env, SESSION_SECRET: process.env.SESSION_SECRET || randomBytes(48).toString('hex') } });
const server = createServer(async (req, res) => {
  try {
    const chunks = []; let size = 0;
    for await (const chunk of req) { size += chunk.length; if (size > 4300000) { res.writeHead(413); res.end('Archivo demasiado grande'); return; } chunks.push(chunk); }
    // El proxy de Vite cambia Host; conservar el origen publico local para validar CSRF.
    const request = new Request(new URL(req.url, 'http://127.0.0.1:5174'), { method: req.method, headers: req.headers, ...(req.method !== 'GET' && req.method !== 'HEAD' ? { body: Buffer.concat(chunks) } : {}) });
    const response = await handler(request, { ip: req.socket.remoteAddress });
    res.writeHead(response.status, Object.fromEntries(response.headers)); res.end(Buffer.from(await response.arrayBuffer()));
  } catch { res.writeHead(500, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ error: 'No se pudo procesar la solicitud.' })); }
});
server.listen(5184, '127.0.0.1');
const vite = await createVite();
try { await vite.listen(); } catch (e) { server.close(); await db.close?.(); throw e; }
console.log(process.env.DATABASE_URL ? 'Usando la base configurada en .env.' : 'Base PostgreSQL local de prueba: .local/postgres. No se usa Neon ni Render.');
vite.printUrls();
const close = async () => { await vite.close(); server.close(); await db.close?.(); process.exit(0); };
process.on('SIGINT', close); process.on('SIGTERM', close);
