import React, { useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import FullCalendar from '@fullcalendar/react';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import esLocale from '@fullcalendar/core/locales/es';
import { Plus, Pencil, Trash2, Check, Clock3, BookOpen, CalendarDays, ListTodo, Flag, Coffee, Trophy, ArrowUpRight, Download, ChevronLeft, ChevronRight, Upload, Save, Search, BarChart3, GraduationCap, FileText, FlaskConical } from 'lucide-react';
import { useApp, LABELS } from './context.jsx';
import { Button, IconButton, Field, PageTitle, Empty, Back, ErrorMessage } from './ui.jsx';
import { CourseForm, EvaluationForm, ClassForm, ConfirmDelete } from './forms.jsx';
import { academicWeek, addDays, daysBetween, weekday, applies, DAY_NAMES, minutes, clock } from '../server/domain.mjs';
import { download } from './api.mjs';

export const dateLabel = (date, options = { weekday: 'long', day: 'numeric', month: 'long' }) => new Intl.DateTimeFormat('es-PE', { timeZone: 'America/Lima', ...options }).format(new Date(date + 'T12:00:00Z'));
const hours = blocks => blocks.reduce((sum, b) => sum + (minutes(b.end) - minutes(b.start)) / 60, 0);
const ordered = items => [...items].sort((a, b) => a.date.localeCompare(b.date) || (a.start || a.time || '').localeCompare(b.start || b.time || ''));

function CourseTag({ course }) { return <span className="course-tag" style={{ '--course': course?.color || '#235B4E' }}><span />{course?.name || 'Curso'}</span>; }
export function BlockList({ blocks }) {
  const { session, mutate } = useApp();
  return <div className="block-list">{ordered(blocks).map(b => {
    const e = session.state.evaluations.find(e => e.id === b.evaluationId);
    const c = session.state.courses.find(c => c.id === e?.courseId);
    return <div className={`study-row ${b.completed ? 'done' : ''}`} key={b.id} style={{ '--course': c?.color }}>
      <button className="check-button" type="button" aria-label={b.completed ? 'Marcar bloque como pendiente' : 'Completar bloque'} aria-pressed={b.completed} onClick={() => mutate(`/blocks/${b.id}/toggle`, 'POST', {})}><Check size={15} /></button>
      <div className="row-main"><Link to={`/evaluaciones/${b.evaluationId}`}>{b.description}</Link><div className="row-meta"><CourseTag course={c} /><span>{b.start}–{b.end}</span></div></div>
      <span className="duration">{(minutes(b.end) - minutes(b.start)) / 60} h</span>
    </div>;
  })}</div>;
}

export function EvaluationList({ items, grouped = true }) {
  const { session, mutate } = useApp();
  const [editing, setEditing] = useState(null), [deleting, setDeleting] = useState(null);
  let previous = null;
  return <><div className="evaluation-list">{ordered(items).map(e => {
    const course = session.state.courses.find(c => c.id === e.courseId);
    const heading = e.date !== previous; previous = e.date;
    const Icon = { EXAMEN: GraduationCap, LABORATORIO: FlaskConical, PROYECTO: BookOpen, PRACTICA: FileText, TAREA: ListTodo }[e.type];
    return <React.Fragment key={e.id}>{grouped && heading && <div className="date-heading"><strong>{dateLabel(e.date)}</strong><span className="week-badge">Semana {academicWeek(session.state.settings.cycleStart, e.date)}</span></div>}
      <div className={`evaluation-row ${e.completed ? 'done' : ''}`} style={{ '--course': course?.color }}>
        <button className="check-button" type="button" aria-label={e.completed ? 'Marcar pendiente' : 'Completar pendiente'} aria-pressed={e.completed} onClick={() => mutate(`/evaluations/${e.id}/toggle`, 'POST', {})}><Check size={15} /></button>
        <div className="row-main"><Link className="row-title" to={`/evaluaciones/${e.id}`}>{e.name}</Link><div className="row-meta"><CourseTag course={course} /><span><Icon size={13} />{LABELS[e.type]}</span>{e.time && <span><Clock3 size={13} />{e.time}</span>}{!grouped && <span>{dateLabel(e.date, { day: 'numeric', month: 'short' })}</span>}</div></div>
        <div className="row-actions"><span className="weight">{e.weight.toFixed(1)}%</span><div><IconButton icon={Pencil} label={`Editar ${e.name}`} onClick={() => setEditing(e)} /><IconButton icon={Trash2} label={`Eliminar ${e.name}`} onClick={() => setDeleting(e)} /></div></div>
      </div></React.Fragment>;
  })}</div>
    {editing && <EvaluationForm evaluation={editing} onClose={() => setEditing(null)} />}
    {deleting && <ConfirmDelete title="Eliminar pendiente" detail={deleting.name} path={`/evaluations/${deleting.id}`} onClose={() => setDeleting(null)} />}
  </>;
}

export function Today() {
  const { session } = useApp();
  const { settings, blocks, evaluations } = session.state;
  const today = session.today;
  const todays = blocks.filter(b => b.date === today && !b.reprogrammed);
  const next = ordered(evaluations.filter(e => !e.completed && e.date >= today));
  const due = next.filter(e => daysBetween(today, e.date) <= 7);
  const done = todays.filter(b => b.completed);
  const week = academicWeek(settings.cycleStart, today);
  return <>
    <section className="welcome-band"><div><span className="eyebrow">{dateLabel(today)}</span><h1>Tu día, {settings.name.split(' ')[0]}.</h1><p>{todays.length ? `${todays.length} bloques de estudio. Avanza a tu ritmo.` : 'Un poco de enfoque, un paso a la vez.'}</p></div><div className="cycle"><small>Avance del ciclo</small><strong>Semana {week}</strong><span>de 16</span><progress value={Math.min(16, Math.max(0, week))} max="16" /></div></section>
    <div className="metrics"><Metric icon={Clock3} label="Estudio de hoy" value={`${hours(done).toFixed(1)} / ${hours(todays).toFixed(1)} h`} /><Metric icon={Flag} label="Entregas en 7 días" value={due.length} /><Metric icon={Trophy} label="Nota en juego" value={`${due.reduce((s, e) => s + e.weight, 0).toFixed(1)}%`} /><Metric icon={CalendarDays} label="Próxima entrega" value={next.length ? `${daysBetween(today, next[0].date)} días` : '—'} /></div>
    {session.state.warnings.length > 0 && <div className="alert warning"><strong>Hay horas pendientes de planificar</strong><p>{session.state.warnings[0]}</p><Link to="/configuracion">Revisar configuración <ArrowUpRight size={14} /></Link></div>}
    <div className="dashboard-columns"><section className="work-section"><div className="section-heading"><h2><Clock3 size={20} />Tu plan de hoy</h2><Link className="button secondary compact" to="/calendario"><CalendarDays size={17} />Calendario</Link></div>
      {todays.length ? <BlockList blocks={todays} /> : <Empty icon={Coffee} title="Nada pendiente para hoy" action={<Link className="button secondary" to={session.state.courses.length ? '/agenda' : '/importar'}>{session.state.courses.length ? 'Ir a mi agenda' : 'Subir mi primer sílabo'}</Link>} />}</section>
      <section className="work-section"><div className="section-heading"><h2><Flag size={20} />Entregas de la semana</h2><Link className="icon-button" to="/agenda" title="Abrir agenda" aria-label="Abrir agenda"><ArrowUpRight size={19} /></Link></div>{due.length ? <EvaluationList items={due.slice(0, 5)} grouped={false} /> : <Empty icon={ListTodo} title="Tu semana está libre de entregas" />}</section></div>
    <section className="week-chart"><div className="section-heading"><h2><BarChart3 size={20} />Tu semana de estudio</h2><span className="muted">Horas planificadas</span></div><div className="chart-bars">{Array.from({ length: 7 }, (_, i) => {
      const d = addDays(today, i); const n = hours(blocks.filter(b => b.date === d && !b.reprogrammed));
      return <div key={d}><div className="chart-track"><span style={{ height: `${Math.min(100, n / Math.max(1, settings.dailyHours) * 100)}%` }} /></div><strong>{n.toFixed(1)} h</strong><small>{i ? DAY_NAMES[weekday(d) - 1] : 'HOY'}</small></div>;
    })}</div></section>
  </>;
}
function Metric({ icon: Icon, label, value }) { return <div className="metric"><span><Icon size={17} />{label}</span><strong>{value}</strong></div>; }

export function Agenda() {
  const { session } = useApp();
  const [type, setType] = useState('ALL'), [status, setStatus] = useState('pending'), [search, setSearch] = useState(''), [adding, setAdding] = useState(false);
  const items = session.state.evaluations.filter(e => (type === 'ALL' || e.type === type) && (status === 'all' || (status === 'done' ? e.completed : !e.completed)) && `${e.name} ${session.state.courses.find(c => c.id === e.courseId)?.name}`.toLowerCase().includes(search.toLowerCase()));
  return <><PageTitle eyebrow="Tu próxima entrega, bajo control" title="Agenda" description="Tus tareas, exámenes y todo lo que viene." action={<Button icon={Plus} disabled={!session.state.courses.length} onClick={() => setAdding(true)}>Nuevo pendiente</Button>} />
    <div className="filter-bar"><div className="tabs" role="tablist" aria-label="Tipo de pendiente">{[['ALL', 'Todo'], ...Object.entries(LABELS)].map(([key, label]) => <button role="tab" aria-selected={type === key} key={key} onClick={() => setType(key)}>{label}</button>)}</div><div className="filter-inputs"><div className="search"><Search size={17} /><input aria-label="Buscar pendientes" placeholder="Buscar pendiente…" value={search} onChange={e => setSearch(e.target.value)} /></div><select aria-label="Estado de pendientes" value={status} onChange={e => setStatus(e.target.value)}><option value="pending">Pendientes</option><option value="done">Completados</option><option value="all">Todos los estados</option></select></div></div>
    {items.length ? <EvaluationList items={items} /> : <Empty icon={ListTodo} title={session.state.courses.length ? 'No hay pendientes en esta vista' : 'Primero registra tus cursos'} action={!session.state.courses.length && <Link className="button primary" to="/cursos"><Plus size={18} />Registrar curso</Link>} />}
    {adding && <EvaluationForm onClose={() => setAdding(false)} />}
  </>;
}

export function Courses() {
  const { session } = useApp(); const [editing, setEditing] = useState(null), [adding, setAdding] = useState(false), [deleting, setDeleting] = useState(null);
  return <><PageTitle eyebrow="Tu ciclo, en un solo lugar" title="Mis cursos" action={<div className="actions"><Link className="button secondary" to="/importar"><Upload size={17} />Subir sílabo</Link><Button icon={Plus} onClick={() => setAdding(true)}>Nuevo curso</Button></div>} />
    {session.state.courses.length ? <div className="course-grid">{session.state.courses.map(c => {
      const evs = session.state.evaluations.filter(e => e.courseId === c.id); const graded = evs.filter(e => e.grade !== null);
      const contribution = graded.reduce((sum, e) => sum + e.grade * e.weight / 100, 0);
      return <article className="course-card" style={{ '--course': c.color }} key={c.id}><div className="course-card-top"><span className="course-symbol"><BookOpen size={23} /></span><div className="actions"><IconButton icon={Pencil} label={`Editar ${c.name}`} onClick={() => setEditing(c)} /><IconButton icon={Trash2} label={`Eliminar ${c.name}`} onClick={() => setDeleting(c)} /></div></div><span className="eyebrow">{c.code}</span><h2><Link to={`/cursos/${c.id}`}>{c.name}</Link></h2><div className="course-details"><span>{c.credits} créditos</span><span>Dificultad {c.difficulty}/5</span></div><div className="course-card-bottom"><span>{evs.filter(e => !e.completed).length} pendientes</span><span>{graded.length ? `${contribution.toFixed(2)} puntos acumulados` : 'Sin notas aún'}</span></div><Link className="course-open" to={`/cursos/${c.id}`}>Ver curso <ArrowUpRight size={17} /></Link></article>;
    })}</div> : <Empty icon={BookOpen} title="Tu próximo ciclo empieza aquí" action={<Button icon={Plus} onClick={() => setAdding(true)}>Crear mi primer curso</Button>} />}
    {(adding || editing) && <CourseForm course={editing} onClose={() => { setAdding(false); setEditing(null); }} />}
    {deleting && <ConfirmDelete title="Eliminar curso" detail={`Se eliminarán ${deleting.name}, sus evaluaciones, clases y bloques de estudio.`} path={`/courses/${deleting.id}`} onClose={() => setDeleting(null)} />}
  </>;
}

export function CourseDetail() {
  const { id } = useParams(), { session } = useApp(); const [adding, setAdding] = useState(false), [editing, setEditing] = useState(false);
  const c = session.state.courses.find(c => c.id === id);
  if (!c) return <><Back to="/cursos">Mis cursos</Back><Empty icon={BookOpen} title="No encontramos este curso" /></>;
  const evs = session.state.evaluations.filter(e => e.courseId === id);
  return <><Back to="/cursos">Mis cursos</Back><PageTitle eyebrow={c.code} title={c.name} description={`${c.credits} créditos · Dificultad ${c.difficulty}/5`} action={<Button variant="secondary" icon={Pencil} onClick={() => setEditing(true)}>Editar curso</Button>} />
    <div className="section-heading"><h2>Evaluaciones y entregas</h2><Button icon={Plus} onClick={() => setAdding(true)}>Agregar</Button></div>
    {evs.length ? <EvaluationList items={evs} /> : <Empty icon={ListTodo} title="Este curso todavía no tiene entregas" />}
    <section className="topics-section"><h2>Temas del curso</h2>{c.topics.length ? <ol>{c.topics.map((topic, i) => <li key={i}>{topic}</li>)}</ol> : <p className="muted">Sin temas registrados.</p>}</section>
    {adding && <EvaluationForm courseId={id} onClose={() => setAdding(false)} />}{editing && <CourseForm course={c} onClose={() => setEditing(false)} />}
  </>;
}

export function EvaluationDetail() {
  const { id } = useParams(), { session } = useApp(); const [editing, setEditing] = useState(false);
  const e = session.state.evaluations.find(e => e.id === id); const course = session.state.courses.find(c => c.id === e?.courseId);
  if (!e) return <><Back to="/agenda">Agenda</Back><Empty icon={ListTodo} title="No encontramos este pendiente" /></>;
  const blocks = session.state.blocks.filter(b => b.evaluationId === id && !b.reprogrammed);
  return <><Back to="/agenda">Agenda</Back><PageTitle eyebrow={course.name} title={e.name} description={`${LABELS[e.type]} · ${dateLabel(e.date)}${e.time ? ' · ' + e.time : ''}`} action={<Button icon={Pencil} variant="secondary" onClick={() => setEditing(true)}>Editar</Button>} />
    <div className="metrics"><Metric icon={Flag} label="Peso en nota final" value={`${e.weight}%`} /><Metric icon={Clock3} label="Preparación estimada" value={`${e.estimatedHours} h`} /><Metric icon={Check} label="Estudio completado" value={`${hours(blocks.filter(b => b.completed)).toFixed(1)} h`} /><Metric icon={Trophy} label="Nota obtenida" value={e.grade ?? '—'} /></div>
    {e.notes && <p className="evaluation-notes">{e.notes}</p>}
    <section className="topics-section"><h2>Plan de repaso</h2><ol>{(e.topics.length ? e.topics : course.topics.length ? course.topics : ['Repasar los apuntes de clase', 'Resolver ejercicios y preguntas de práctica', 'Revisar errores y realizar un repaso final']).map((t, i) => <li key={i}>{t}</li>)}</ol></section>
    <section className="work-section"><div className="section-heading"><h2><Clock3 size={20} />Bloques de estudio</h2></div>{blocks.length ? <>{[...new Set(ordered(blocks).map(b => b.date))].map(d => <div key={d}><h3 className="date-heading">{dateLabel(d)}</h3><BlockList blocks={blocks.filter(b => b.date === d)} /></div>)}</> : <Empty icon={Clock3} title={e.completed ? 'Entrega completada' : 'No hay bloques de estudio planificados'} />}</section>
    {editing && <EvaluationForm evaluation={e} onClose={() => setEditing(false)} />}
  </>;
}

export function Schedule() {
  const { session } = useApp(); const [editing, setEditing] = useState(null), [adding, setAdding] = useState(false), [deleting, setDeleting] = useState(null);
  const [week, setWeek] = useState(academicWeek(session.state.settings.cycleStart, session.today) % 2 ? 'A' : 'B');
  return <><PageTitle eyebrow="Un espacio para cada cosa" title="Mi horario" action={<div className="actions"><Link className="button secondary" to="/importar?tipo=horario"><Upload size={17} />Importar horario</Link><Button icon={Plus} disabled={!session.state.courses.length} onClick={() => setAdding(true)}>Nueva clase</Button></div>} />
    <div className="section-heading"><div className="segments" aria-label="Frecuencia del horario">{['A', 'B'].map(v => <button key={v} aria-pressed={week === v} onClick={() => setWeek(v)}>Semana {v}</button>)}</div><span className="muted">{week === 'A' ? 'Semanas impares' : 'Semanas pares'}</span></div>
    <div className="schedule-grid">{DAY_NAMES.map((day, i) => <section className="schedule-day" key={day}><h2>{day}</h2>{session.state.classes.filter(c => c.weekday === i + 1 && (c.frequency === 'SEMANAL' || c.frequency === week)).sort((a, b) => a.start.localeCompare(b.start)).map(cl => {
      const c = session.state.courses.find(c => c.id === cl.courseId);
      return <article className="class-entry" style={{ '--course': c.color }} key={cl.id}><time>{cl.start}–{cl.end}</time><strong>{c.name}</strong><small>{c.code}{cl.detail && ' · ' + cl.detail}</small><div className="actions"><IconButton icon={Pencil} label={`Editar clase de ${c.code}`} onClick={() => setEditing(cl)} /><IconButton icon={Trash2} label={`Eliminar clase de ${c.code}`} onClick={() => setDeleting(cl)} /></div></article>;
    })}<span className="day-end">{session.state.classes.some(c => c.weekday === i + 1 && (c.frequency === 'SEMANAL' || c.frequency === week)) ? '' : 'Sin clases'}</span></section>)}</div>
    {!session.state.courses.length && <Link className="button secondary" to="/cursos"><Plus size={17} />Registrar un curso</Link>}
    {(adding || editing) && <ClassForm item={editing} onClose={() => { setAdding(false); setEditing(null); }} />}
    {deleting && <ConfirmDelete title="Eliminar clase" detail={`${DAY_NAMES[deleting.weekday - 1]} · ${deleting.start}–${deleting.end}`} path={`/classes/${deleting.id}`} onClose={() => setDeleting(null)} />}
  </>;
}

export function CalendarPage() {
  const { session, setNotice } = useApp(); const calendar = useRef(); const navigate = useNavigate();
  const [title, setTitle] = useState(''), [mode, setMode] = useState('dayGridMonth'), [adding, setAdding] = useState(null), [editingClass, setEditingClass] = useState(null);
  const [filter, setFilter] = useState('all'), [instructions, setInstructions] = useState(false);
  const { courses, evaluations, blocks, classes, settings } = session.state;
  const course = id => courses.find(c => c.id === id);
  const events = [];
  if (filter === 'all' || filter === 'evaluations') for (const e of evaluations) if (!e.completed) events.push({ id: e.id, title: `${course(e.courseId)?.code} · ${e.name}`, start: e.date + (e.time ? 'T' + e.time : ''), allDay: !e.time, backgroundColor: course(e.courseId)?.color, borderColor: course(e.courseId)?.color, extendedProps: { kind: 'evaluation' } });
  if (filter === 'all' || filter === 'study') for (const b of blocks) if (!b.reprogrammed) {
    const e = evaluations.find(e => e.id === b.evaluationId);
    events.push({ id: b.id, title: `${b.completed ? '✓ ' : ''}${b.description}`, start: b.date + 'T' + b.start, end: b.date + 'T' + b.end, backgroundColor: course(e?.courseId)?.color, borderColor: course(e?.courseId)?.color, extendedProps: { kind: 'study', evaluationId: b.evaluationId } });
  }
  if (filter === 'all' || filter === 'classes') for (let n = 0; n < 16 * 7; n++) {
    const date = addDays(settings.cycleStart, n);
    for (const cl of classes.filter(c => applies(c, date, settings.cycleStart))) events.push({ id: `${cl.id}:${date}`, title: `${course(cl.courseId)?.code} · Clase`, start: date + 'T' + cl.start, end: date + 'T' + cl.end, backgroundColor: '#D7E7E0', borderColor: course(cl.courseId)?.color, textColor: '#193C30', extendedProps: { kind: 'class', classId: cl.id } });
  }
  return <><PageTitle title="Calendario" eyebrow="Todo en un solo lugar" description="Evaluaciones, bloques de estudio y clases de todos tus cursos." action={<div className="d-flex flex-column align-items-lg-end gap-2"><Button icon={Download} onClick={async () => { try { await download('/export/calendar', 'plan-de-estudio.ics'); } catch (e) { setNotice({ error: e.message }); } }}>Exportar a Google Calendar</Button><button type="button" className="btn btn-link btn-sm p-0 text-body-secondary" aria-expanded={instructions} onClick={() => setInstructions(!instructions)}>¿Cómo lo importo?</button></div>} />
    {instructions && <div className="aviso-dorado"><i className="bi bi-info-circle-fill" /><ol className="mb-0 ps-3"><li>Pulsa <strong>Exportar a Google Calendar</strong>: se descarga <code>plan-de-estudio.ics</code>.</li><li>Abre <a href="https://calendar.google.com/calendar/r/settings/export" target="_blank" rel="noopener">Google Calendar → Configuración → Importar y exportar</a>.</li><li>Elige el archivo, selecciona en qué calendario guardarlo y pulsa <strong>Importar</strong>.</li><li>Si luego actualizas tu plan, vuelve a exportar e importar.</li></ol></div>}
    <div className="leyenda mb-3">{courses.map(c => <span className="chip-curso" style={{ '--color-curso': c.color }} key={c.id}>{c.name}</span>)}</div>
    <div className="tarjeta"><div className="tarjeta-cuerpo pt-4" id="calendario"><FullCalendar ref={calendar} plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]} locale={esLocale} initialDate={session.today} initialView="dayGridMonth" firstDay={1} headerToolbar={{ left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,timeGridDay' }} height="auto" events={events} nowIndicator dayMaxEvents={3} slotMinTime="07:00:00" slotMaxTime="24:00:00" datesSet={info => setTitle(info.view.title)}
      dateClick={info => { if (courses.length) setAdding(info.dateStr.slice(0, 10)); }}
      eventClick={({ event }) => { if (event.extendedProps.kind === 'class') setEditingClass(classes.find(c => c.id === event.extendedProps.classId)); else navigate(`/evaluaciones/${event.extendedProps.evaluationId || event.id}`); }} /></div></div><p className="small text-body-secondary mt-3 mb-0"><i className="bi bi-flag-fill me-1" />Evaluaciones en la parte superior de cada día · <i className="bi bi-clock me-1" />Bloques de estudio con hora · Zonas sombreadas: clases (vista semanal)</p>
    {adding && <EvaluationForm date={adding} onClose={() => setAdding(null)} />}{editingClass && <ClassForm item={editingClass} onClose={() => setEditingClass(null)} />}
  </>;
}

export function SettingsPage() {
  const { session, mutate, busy, setNotice } = useApp(); const s = session.state.settings;
  const [days, setDays] = useState(s.studyDays), [error, setError] = useState('');
  const submit = async e => {
    e.preventDefault(); setError(''); const f = new FormData(e.currentTarget);
    if (!days.length) { setError('Elige al menos un día de estudio.'); return; }
    await mutate('/settings', 'PUT', { name: f.get('name'), cycleStart: f.get('cycleStart'), dailyHours: Number(f.get('dailyHours')), totalDailyHours: Number(f.get('totalDailyHours')), weeklyCourseHours: Number(f.get('weeklyCourseHours')), studyDays: days, sleepTime: f.get('sleepTime') });
  };
  return <><PageTitle title="Configuración" eyebrow="Tu espacio, a tu ritmo" /><form className="settings-form" onSubmit={submit}>
    <section><h2>Mi perfil</h2><Field label="Tu nombre" name="name" defaultValue={s.name} maxLength={100} required /><p className="muted">{session.user.email}</p></section>
    <section><h2>Mi ciclo</h2><Field label="Lunes de la semana 1" name="cycleStart" type="date" defaultValue={s.cycleStart} required /><p className="muted">Ahora estás en la semana {academicWeek(s.cycleStart, session.today)}.</p></section>
    <section><h2>Mis límites de estudio</h2><div className="form-grid"><Field label="Horas de estudio al día" name="dailyHours" type="number" min="0.5" max="12" step="0.5" defaultValue={s.dailyHours} required /><Field label="Carga diaria máxima (clases + estudio)" name="totalDailyHours" type="number" min="3" max="16" step="0.5" defaultValue={s.totalDailyHours} required /><Field label="Horas por curso a la semana" name="weeklyCourseHours" type="number" min="1" max="20" step="0.5" defaultValue={s.weeklyCourseHours} required /><Field label="Hora de dormir" name="sleepTime" type="time" defaultValue={s.sleepTime} required /></div>
      <fieldset className="study-days"><legend>Días de estudio</legend>{DAY_NAMES.map((day, i) => <label key={day}><input type="checkbox" checked={days.includes(i + 1)} onChange={e => setDays(previous => e.target.checked ? [...previous, i + 1].sort() : previous.filter(n => n !== i + 1))} /><span>{day}</span></label>)}</fieldset>
      <ErrorMessage message={error} /></section><div className="form-actions"><Button type="submit" icon={Save} busy={busy}>Guardar configuración</Button></div>
    </form><section className="settings-extra"><h2>Mis datos</h2><Button variant="secondary" icon={Download} onClick={async () => { try { await download('/export/data', 'mis-datos-planificador.json'); } catch (e) { setNotice({ error: e.message }); } }}>Descargar copia de mis datos</Button><p className="muted">IA disponible: {session.provider.configured.length ? session.provider.configured.join(', ') : 'sin configurar'}.</p></section>
  </>;
}
