import { neonDatabase } from '../../server/db.mjs';
import { createHandler } from '../../server/api.mjs';

let handler;
export default async (request, context) => {
  if (!process.env.DATABASE_URL || !process.env.SESSION_SECRET || process.env.SESSION_SECRET.length < 32) {
    return Response.json({ error: 'Falta configurar DATABASE_URL y SESSION_SECRET en Netlify.' }, { status: 503, headers: { 'Cache-Control': 'no-store' } });
  }
  try {
    handler ??= createHandler({ db: neonDatabase(process.env.DATABASE_URL), env: process.env });
    return handler(request, context);
  } catch {
    return Response.json({ error: 'No se pudo inicializar la conexion. Revisa las variables privadas en Netlify.' }, { status: 503, headers: { 'Cache-Control': 'no-store' } });
  }
};
