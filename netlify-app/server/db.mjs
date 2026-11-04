import { neon } from '@neondatabase/serverless';

export const TABLES = ['pa_netlify_users', 'pa_netlify_state', 'pa_netlify_sessions', 'pa_netlify_limits'];
export const ddl = [
  `CREATE TABLE IF NOT EXISTS pa_netlify_users (
    id UUID PRIMARY KEY, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
  )`,
  `CREATE TABLE IF NOT EXISTS pa_netlify_state (
    user_id UUID PRIMARY KEY REFERENCES pa_netlify_users(id) ON DELETE CASCADE,
    document JSONB NOT NULL, version INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
  )`,
  `CREATE TABLE IF NOT EXISTS pa_netlify_sessions (
    token_hash TEXT PRIMARY KEY, user_id UUID NOT NULL REFERENCES pa_netlify_users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
  )`,
  `CREATE INDEX IF NOT EXISTS pa_netlify_sessions_expiry ON pa_netlify_sessions(expires_at)`,
  `CREATE TABLE IF NOT EXISTS pa_netlify_limits (
    key TEXT PRIMARY KEY, hits INTEGER NOT NULL, expires_at TIMESTAMPTZ NOT NULL
  )`
];

export function neonDatabase(url) {
  if (!url || !/^postgres(ql)?:\/\//.test(url)) throw new Error('Falta configurar DATABASE_URL en el servidor.');
  const sql = neon(url);
  return {
    query: async (query, params = []) => ({ rows: await sql.query(query, params) }),
    transaction: async statements => sql.transaction(statements.map(([query, params = []]) => sql.query(query, params)))
  };
}

export async function prepare(db) {
  for (const statement of ddl) await db.query(statement);
}
