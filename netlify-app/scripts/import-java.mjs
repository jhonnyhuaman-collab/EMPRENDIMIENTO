import { randomUUID } from 'node:crypto';
import { neonDatabase } from '../server/db.mjs';
import { convertJava, assertSeparateDatabases } from '../server/migrate-java.mjs';

assertSeparateDatabases(process.env.JAVA_DATABASE_URL, process.env.DATABASE_URL);
const source = neonDatabase(process.env.JAVA_DATABASE_URL), target = neonDatabase(process.env.DATABASE_URL);
const names = { students: 'estudiante', courses: 'curso', topics: 'tema', evaluations: 'evaluacion', classes: 'horario_clase', blocks: 'bloque_estudio', links: 'evaluacion_tema' };
const data = {};
for (const [name, table] of Object.entries(names)) data[name] = (await source.query(`SELECT * FROM ${table}`)).rows;
const converted = convertJava(data);
console.log('Copia preparada:', converted.length, 'cuentas. No se escribio en la base origen.');
if (process.env.CONFIRM_IMPORT !== 'YES') {
  console.log('Modo revision: no se escribio en el destino. Repetir con CONFIRM_IMPORT=YES para copiar.');
  process.exit(0);
}
let imported = 0, skipped = 0;
for (const user of converted) {
  if ((await target.query('SELECT id FROM pa_netlify_users WHERE email=$1', [user.email])).rows.length) { skipped++; continue; }
  const id = randomUUID();
  await target.transaction([
    ['INSERT INTO pa_netlify_users(id,email,password_hash) VALUES($1,$2,$3)', [id, user.email, user.passwordHash]],
    ['INSERT INTO pa_netlify_state(user_id,document) VALUES($1,$2::jsonb)', [id, JSON.stringify(user.document)]]
  ]);
  imported++;
}
console.log('Cuentas copiadas:', imported, '· existentes omitidas:', skipped, '· la base Java permanece intacta.');
