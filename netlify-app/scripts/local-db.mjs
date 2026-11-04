import { PGlite } from '@electric-sql/pglite';
import { prepare } from '../server/db.mjs';
export async function localDatabase(location) {
  const pg = new PGlite(location);
  await pg.waitReady;
  const db = {
    query: (text, params = []) => pg.query(text, params),
    transaction: statements => pg.transaction(async tx => {
      const results = [];
      for (const [text, params = []] of statements) results.push((await tx.query(text, params)).rows);
      return results;
    }),
    close: () => pg.close()
  };
  await prepare(db);
  return db;
}
