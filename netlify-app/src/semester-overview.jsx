import React from 'react';
import { Link } from 'react-router-dom';
import { useApp } from './context.jsx';
import { addDays, academicWeek, minutes } from '../server/domain.mjs';
export function SemesterOverview({ courseId }) {
  const { session } = useApp(); const { settings, courses, evaluations, blocks } = session.state;
  const selected = evaluations.filter(e => !courseId || e.courseId === courseId);
  const ids = new Set(selected.map(e => e.id));
  return <section className="mt-4"><div className="tarjeta-cabecera"><h2 className="tarjeta-titulo"><span className="icono-titulo"><i className="bi bi-calendar2-range" /></span>Tu preparación en 16 semanas</h2></div><p className="small text-body-secondary">Temas en orden y repaso final antes del examen. Solo se planifica desde hoy; las semanas pasadas no se rellenan.</p><div className="semester-weeks">{Array.from({ length: 16 }, (_, n) => {
    const start = addDays(settings.cycleStart, n * 7), end = addDays(start, 7);
    const items = blocks.filter(b => ids.has(b.evaluationId) && !b.reprogrammed && b.date >= start && b.date < end).sort((a, b) => a.date.localeCompare(b.date) || a.start.localeCompare(b.start));
    const hours = items.reduce((sum, b) => sum + (minutes(b.end) - minutes(b.start)) / 60, 0);
    const exams = selected.filter(e => e.date >= start && e.date < end);
    return <details className="tarjeta semester-week" key={n} open={academicWeek(settings.cycleStart, session.today) === n + 1}><summary><strong>Semana {n + 1}</strong><span>{start.slice(8)}/{start.slice(5, 7)} – {addDays(end, -1).slice(8)}/{addDays(end, -1).slice(5, 7)}</span><span>{hours.toFixed(1)} h · {items.length} bloques{exams.length ? ` · ${exams.length} evaluaciones` : ''}</span></summary><div className="tarjeta-cuerpo pt-0">{exams.map(e => <p key={e.id}><i className="bi bi-flag-fill" /> <Link to={`/evaluaciones/${e.id}`}>{e.name}</Link> · {e.date}</p>)}{items.length ? <ul className="semester-topics">{items.map(b => { const e = selected.find(e => e.id === b.evaluationId), c = courses.find(c => c.id === e.courseId); return <li key={b.id}><span className="chip-curso" style={{ '--color-curso': c.color }}>{c.code}</span><Link to={`/evaluaciones/${e.id}`}>{b.description}</Link><small>{b.date} · {b.start}–{b.end}{b.completed ? ' · Completado' : ''}</small></li>; })}</ul> : <p className="small text-body-secondary mb-0">{end <= session.today ? 'Semana pasada.' : 'Sin bloques planificados. Revisa las evaluaciones, los temas y tus límites de estudio.'}</p>}</div></details>;
  })}</div></section>;
}
