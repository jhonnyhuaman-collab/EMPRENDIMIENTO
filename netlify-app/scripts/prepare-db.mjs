import { neonDatabase, prepare } from '../server/db.mjs';
if (process.env.CONFIRM_NEW_DATABASE !== 'YES') {
  throw new Error('Usa una base nueva y ejecuta con CONFIRM_NEW_DATABASE=YES. No preparar la base de Java por accidente.');
}
await prepare(neonDatabase(process.env.DATABASE_URL));
console.log('Tablas pa_netlify_* preparadas. No se modificaron tablas de la version Java.');
