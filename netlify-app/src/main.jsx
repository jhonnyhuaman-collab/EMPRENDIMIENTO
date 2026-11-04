import React, { Suspense, lazy, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Routes, Route, NavLink, Link, Navigate, useLocation } from 'react-router-dom';
import { Sun, CalendarDays, ListTodo, Clock3, BookOpen, CloudUpload, Settings, LogOut, RefreshCw, Eye, EyeOff, Mail, LockKeyhole, LogIn, UserRoundPlus, X, LoaderCircle } from 'lucide-react';
import { request } from './api.mjs';
import { Button, IconButton, Field, ErrorMessage, InstallButton } from './ui.jsx';
import './styles.css';
import '@fontsource/plus-jakarta-sans/latin-400.css';
import '@fontsource/plus-jakarta-sans/latin-500.css';
import '@fontsource/plus-jakarta-sans/latin-600.css';
import '@fontsource/plus-jakarta-sans/latin-700.css';
import { AppContext, useApp } from './context.jsx';
const page = name => lazy(() => import('./pages.jsx').then(module => ({ default: module[name] })));
const Today = page('Today'), Agenda = page('Agenda'), Courses = page('Courses'), CourseDetail = page('CourseDetail');
const EvaluationDetail = page('EvaluationDetail'), Schedule = page('Schedule'), CalendarPage = page('CalendarPage'), SettingsPage = page('SettingsPage');
const ImportPage = lazy(() => import('./imports.jsx').then(module => ({ default: module.ImportPage })));
const navigation = [
  ['/', 'Hoy', Sun], ['/calendario', 'Calendario', CalendarDays], ['/agenda', 'Agenda', ListTodo], ['/horario', 'Mi horario', Clock3],
  ['/cursos', 'Mis cursos', BookOpen], ['/importar', 'Subir sílabo', CloudUpload], ['/configuracion', 'Configuración', Settings]
];

function Brand({ to = '/' }) {
  return <Link className="brand" to={to}><img src="/icons/icon-192.png" alt="" width="48" height="48" /><span><strong>Planificador</strong><small>Académico</small></span></Link>;
}

function Auth() {
  const { reload, notice, setNotice } = useApp();
  const location = useLocation();
  const registering = location.pathname === '/registro';
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => { setError(''); setVisible(false); }, [registering]);
  const submit = async event => {
    event.preventDefault(); setBusy(true); setError('');
    const form = new FormData(event.currentTarget);
    try {
      await request(registering ? '/register' : '/login', { method: 'POST', data: { email: form.get('email'), password: form.get('password') } });
      await reload(); setNotice(null);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  return <main className="auth-page"><div className="auth-content"><Brand to="/login" />
    <section className="auth-panel" key={registering ? 'register' : 'login'}>
      <div className="auth-kicker"><span className="metal-icon">{registering ? <UserRoundPlus size={22} /> : <LogIn size={22} />}</span>{registering ? 'Empecemos' : 'Tu espacio de estudio'}</div>
      <h1>{registering ? 'Crea tu cuenta.' : 'Qué bueno verte.'}</h1>
      <p>{registering ? 'Tu próxima semana empieza aquí.' : 'Tu semana, a tu ritmo.'}</p>
      <ErrorMessage message={error || notice?.error} />
      <form onSubmit={submit}>
        <Field label="Correo electrónico"><div className="input-icon"><Mail size={18} /><input name="email" type="email" autoComplete="username" maxLength={254} placeholder="nombre@universidad.edu.pe" required /></div></Field>
        <Field label="Contraseña"><div className="input-icon"><LockKeyhole size={18} /><input name="password" type={visible ? 'text' : 'password'} minLength={8} maxLength={72} autoComplete={registering ? 'new-password' : 'current-password'} required />
          <IconButton icon={visible ? EyeOff : Eye} label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={visible} onClick={() => setVisible(!visible)} /></div></Field>
        {registering && <small className="muted">Al menos 8 caracteres.</small>}
        <Button type="submit" busy={busy} icon={registering ? UserRoundPlus : LogIn}>{registering ? 'Crear mi cuenta' : 'Iniciar sesión'}</Button>
      </form>
      <div className="auth-switch">{registering ? <Link to="/login">Volver al inicio de sesión</Link> : <>¿Primera vez? <Link to="/registro">Crear cuenta</Link></>}</div>
      <InstallButton />
    </section><p className="auth-footer">Un día a la vez. Tú puedes.</p></div></main>;
}

function Shell() {
  const { session, logout, mutate, busy } = useApp();
  const path = useLocation().pathname;
  const current = navigation.find(([url]) => url === '/' ? path === '/' : path.startsWith(url));
  const initial = session.state.settings.name.slice(0, 1).toUpperCase();
  return <>
    <aside className="sidebar"><Brand /><nav aria-label="Menú principal"><span className="nav-section">MI SEMANA</span>
      {navigation.map(([to, text, Icon], i) => <React.Fragment key={to}>{i === 4 && <span className="nav-section">ACADÉMICO</span>}
        <NavLink to={to} end={to === '/'}><span className="nav-icon"><Icon size={18} /></span>{text}{to === '/importar' && <small className="ai-badge">IA</small>}</NavLink></React.Fragment>)}
      <InstallButton />
    </nav><div className="sidebar-bottom"><div className="profile"><span className="avatar">{initial}</span><div><strong>{session.state.settings.name}</strong><small title={session.user.email}>{session.user.email}</small></div><IconButton icon={LogOut} label="Cerrar sesión" onClick={logout} /></div>
      <Button variant="metal" icon={RefreshCw} busy={busy} onClick={() => mutate('/plan', 'POST', {})}>Actualizar mi plan</Button></div>
    </aside>
    <header className="topbar"><div><span className="muted">Mi espacio</span><span className="breadcrumb-separator">/</span>{current?.[1] || 'Mi curso'}</div><Link to="/configuracion" className="top-profile"><span>{session.state.settings.name}<small>Mi perfil</small></span><span className="avatar">{initial}</span></Link></header>
    <header className="mobile-header"><Brand /><div><Link className="icon-button" to="/configuracion" title="Configuración" aria-label="Configuración"><Settings size={19} /></Link><IconButton icon={LogOut} label="Cerrar sesión" onClick={logout} /></div></header>
    <div className="mobile-profile"><span>{session.state.settings.name}</span><InstallButton /></div>
    <main className="app-main"><div className="page-content"><Suspense fallback={<p className="muted">Cargando…</p>}><Routes>
      <Route path="/" element={<Today />} /><Route path="/agenda" element={<Agenda />} />
      <Route path="/cursos" element={<Courses />} /><Route path="/cursos/:id" element={<CourseDetail />} />
      <Route path="/evaluaciones/:id" element={<EvaluationDetail />} /><Route path="/horario" element={<Schedule />} />
      <Route path="/calendario" element={<CalendarPage />} /><Route path="/configuracion" element={<SettingsPage />} />
      <Route path="/importar" element={<ImportPage />} /><Route path="*" element={<Navigate to="/" replace />} />
    </Routes></Suspense><footer className="page-footer">Planificador Académico · UTEC 2026-2</footer></div></main>
    <nav className="bottom-nav" aria-label="Navegación principal">{navigation.slice(0, 5).map(([to, text, Icon]) => <NavLink key={to} to={to} end={to === '/'}><Icon size={21} /><span>{text.replace('Mi ', '').replace('Mis ', '')}</span></NavLink>)}</nav>
  </>;
}

function App() {
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);
  const reload = async () => {
    try { const result = await request('/session'); setSession(result); return result; }
    catch (e) { if (e.status === 401) setSession(null); else setNotice({ error: e.message }); return null; }
    finally { setLoading(false); }
  };
  useEffect(() => { reload(); if ('serviceWorker' in navigator && window.isSecureContext) navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' }).catch(() => {}); }, []);
  useEffect(() => { if (notice && !notice.error) { const id = setTimeout(() => setNotice(null), 7000); return () => clearTimeout(id); } }, [notice]);
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
  return <AppContext.Provider value={{ session, mutate, reload, logout, busy, notice, setNotice }}>
    {loading ? <div className="loading-page"><img src="/icons/icon-192.png" alt="Planificador Académico" width="64" height="64" /><LoaderCircle size={24} className="spin" /><span>Cargando tu espacio…</span></div> : session ? <Shell /> : <Auth />}
    {session && notice && <div className={`toast ${notice.error ? 'error' : ''}`} role={notice.error ? 'alert' : 'status'}><span>{notice.error || notice.message}</span><IconButton icon={X} label="Cerrar aviso" onClick={() => setNotice(null)} /></div>}
  </AppContext.Provider>;
}

createRoot(document.getElementById('root')).render(<BrowserRouter><App /></BrowserRouter>);
