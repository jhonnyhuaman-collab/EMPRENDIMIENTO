import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { LoaderCircle } from 'lucide-react';
import { request } from './api.mjs';
import { AppContext } from './context.jsx';
import { Auth, Shell } from './render-shell.jsx';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
import '@fontsource/plus-jakarta-sans/latin-400.css';
import '@fontsource/plus-jakarta-sans/latin-500.css';
import '@fontsource/plus-jakarta-sans/latin-600.css';
import '@fontsource/plus-jakarta-sans/latin-700.css';
import '@fontsource/fraunces/latin-600.css';
import './render-adapter.css';

function App() {
  const [session, setSession] = useState(null), [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [notice, setNotice] = useState(null);
  const reload = async () => {
    try { const result = await request('/session'); setSession(result); return result; }
    catch (e) { if (e.status === 401) setSession(null); else setNotice({ error: e.message }); return null; }
    finally { setLoading(false); }
  };
  useEffect(() => { reload(); if ('serviceWorker' in navigator && window.isSecureContext) navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' }).catch(() => {}); }, []);
  const mutate = async (path, method, data, extra = {}) => {
    if (busy) return false;
    setBusy(true);
    try {
      const result = await request(path, { method, data: { version: session.version, data, ...extra }, csrfToken: session.csrfToken });
      setSession(result); setNotice({ message: path === '/plan' ? 'Tu plan se ha actualizado.' : 'Cambios guardados.' }); return result;
    } catch (e) { setNotice({ error: e.message }); if (e.status === 401) setSession(null); if (e.status === 409) await reload(); return false; }
    finally { setBusy(false); }
  };
  const logout = async () => {
    try { await request('/logout', { method: 'POST', data: {}, csrfToken: session.csrfToken }); setSession(null); setNotice(null); }
    catch (e) { setNotice({ error: e.message }); }
  };
  return <AppContext.Provider value={{ session, mutate, reload, logout, busy, notice, setNotice }}>{loading ? <div className="loading-page"><img src="/images/pwa/icon-192-v2.png" alt="Planificador Académico" width="64" height="64" /><LoaderCircle size={24} className="spin" /><span>Cargando tu espacio…</span></div> : session ? <Shell /> : <Auth />}</AppContext.Provider>;
}
createRoot(document.getElementById('root')).render(<BrowserRouter><App /></BrowserRouter>);
