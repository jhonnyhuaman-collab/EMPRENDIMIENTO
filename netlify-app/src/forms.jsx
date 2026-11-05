import React, { useState } from 'react';
import { Save, Trash2 } from 'lucide-react';
import { Field, Button, Modal } from './ui.jsx';
import { useApp, LABELS } from './context.jsx';
import { DAY_NAMES } from '../server/domain.mjs';

export const COLORS = ['#235B4E', '#456A9A', '#8D5B84', '#9C6B35', '#477C7A', '#A05252'];
export function CourseForm({ course, onClose }) {
  const { mutate, busy } = useApp();
  const [color, setColor] = useState(course?.color || COLORS[0]);
  const submit = async e => {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    const data = { code: f.get('code'), name: f.get('name'), credits: Number(f.get('credits')), difficulty: Number(f.get('difficulty')), color,
      topics: String(f.get('topics')).split('\n').map(s => s.trim()).filter(Boolean) };
    if (await mutate(course ? `/courses/${course.id}` : '/courses', course ? 'PUT' : 'POST', data)) onClose();
  };
  return <Modal title={course ? 'Editar curso' : 'Nuevo curso'} onClose={onClose}><form onSubmit={submit}>
    <Field label="Nombre del curso" name="name" defaultValue={course?.name || ''} maxLength={160} required />
    <div className="form-grid"><Field label="Código" name="code" defaultValue={course?.code || ''} maxLength={24} required />
      <Field label="Créditos" name="credits" type="number" min="0.5" max="20" step="0.5" defaultValue={course?.credits || 3} required /></div>
    <Field label="Dificultad"><select name="difficulty" defaultValue={course?.difficulty || 3}>{[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n} · {['Muy ligera', 'Ligera', 'Media', 'Exigente', 'Muy exigente'][n - 1]}</option>)}</select></Field>
    <fieldset className="color-picker"><legend>Color del curso</legend>{COLORS.map(c => <button key={c} type="button" style={{ backgroundColor: c }} aria-label={`Color ${c}`} aria-pressed={color === c} onClick={() => setColor(c)} />)}<input type="color" value={color} onChange={e => setColor(e.target.value)} aria-label="Otro color" /></fieldset>
    <Field label="Temas del curso"><textarea name="topics" defaultValue={course?.topics?.join('\n') || ''} rows="4" maxLength={20000} /></Field>
    <div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" busy={busy} icon={Save}>Guardar curso</Button></div>
  </form></Modal>;
}
export function EvaluationForm({ evaluation, courseId, date, onClose }) {
  const { session, mutate, busy } = useApp();
  const { courses } = session.state;
  const submit = async e => {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    const data = { courseId: f.get('courseId'), name: f.get('name'), type: f.get('type'), date: f.get('date'), time: f.get('time') || null,
      weight: Number(f.get('weight')), estimatedHours: f.get('hours') ? Number(f.get('hours')) : null, grade: f.get('grade') !== '' ? Number(f.get('grade')) : null,
      notes: f.get('notes'), topics: String(f.get('topics')).split('\n').map(s => s.trim()).filter(Boolean), completed: evaluation?.completed || false,
      origin: evaluation?.origin || 'AGENDA', week: evaluation?.week || null };
    if (await mutate(evaluation ? `/evaluations/${evaluation.id}` : '/evaluations', evaluation ? 'PUT' : 'POST', data)) onClose();
  };
  return <Modal title={evaluation ? 'Editar pendiente' : 'Nuevo pendiente'} onClose={onClose}><form onSubmit={submit}>
    <Field label="¿Qué tienes que entregar o rendir?" name="name" defaultValue={evaluation?.name || ''} maxLength={160} required />
    <div className="form-grid"><Field label="Curso"><select name="courseId" defaultValue={evaluation?.courseId || courseId || courses[0]?.id} required>{courses.map(c => <option key={c.id} value={c.id}>{c.code} · {c.name}</option>)}</select></Field>
      <Field label="Tipo"><select name="type" defaultValue={evaluation?.type || 'TAREA'}>{Object.entries(LABELS).map(([v, t]) => <option key={v} value={v}>{t}</option>)}</select></Field></div>
    <div className="form-grid"><Field label="Fecha de entrega" name="date" type="date" defaultValue={evaluation?.date || date || session.today} required />
      <Field label="Hora límite" name="time" type="time" defaultValue={evaluation?.time || ''} /></div>
    <div className="form-grid"><Field label="Peso en la nota (%)" name="weight" type="number" min="0" max="100" step="any" defaultValue={evaluation?.weight || 0} required />
      <Field label="Horas de preparación" name="hours" type="number" min="0.5" max="200" step="0.5" placeholder="Auto" defaultValue={evaluation?.estimatedHours || ''} /></div>
    <Field label="Nota obtenida (opcional)" name="grade" type="number" min="0" max="20" step="0.1" defaultValue={evaluation?.grade ?? ''} />
    <Field label="Notas"><textarea name="notes" defaultValue={evaluation?.notes || ''} maxLength={4000} rows="2" /></Field>
    <Field label="Temas de repaso"><textarea name="topics" defaultValue={evaluation?.topics?.join('\n') || ''} maxLength={20000} rows="3" /></Field>
    <div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" busy={busy} icon={Save}>Guardar pendiente</Button></div>
  </form></Modal>;
}
export function ClassForm({ item, weekday = 1, onClose }) {
  const { session, mutate, busy } = useApp();
  const submit = async e => {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    const data = { courseId: f.get('courseId'), weekday: Number(f.get('weekday')), start: f.get('start'), end: f.get('end'), frequency: f.get('frequency'), detail: f.get('detail') };
    if (await mutate(item ? `/classes/${item.id}` : '/classes', item ? 'PUT' : 'POST', data)) onClose();
  };
  return <Modal title={item ? 'Editar clase' : 'Nueva clase'} onClose={onClose}><form onSubmit={submit}>
    <Field label="Curso"><select name="courseId" defaultValue={item?.courseId || session.state.courses[0]?.id} required>{session.state.courses.map(c => <option key={c.id} value={c.id}>{c.code} · {c.name}</option>)}</select></Field>
    <div className="form-grid"><Field label="Día"><select name="weekday" defaultValue={item?.weekday || weekday}>{DAY_NAMES.map((day, i) => <option value={i + 1} key={day}>{day}</option>)}</select></Field>
      <Field label="Frecuencia"><select name="frequency" defaultValue={item?.frequency || 'SEMANAL'}><option value="SEMANAL">Todas las semanas</option><option value="A">Semana A · impares</option><option value="B">Semana B · pares</option></select></Field></div>
    <div className="form-grid"><Field label="Hora de inicio" name="start" type="time" defaultValue={item?.start || '09:00'} required /><Field label="Hora de fin" name="end" type="time" defaultValue={item?.end || '11:00'} required /></div>
    <Field label="Detalle / aula" name="detail" maxLength={200} defaultValue={item?.detail || ''} />
    <div className="form-actions"><Button type="button" variant="secondary" onClick={onClose}>Cancelar</Button><Button type="submit" busy={busy} icon={Save}>Guardar clase</Button></div>
  </form></Modal>;
}
export function ConfirmDelete({ title, detail, path, onClose }) {
  const { mutate, busy } = useApp();
  return <Modal title={title} onClose={onClose}><p className="muted">{detail}</p><div className="form-actions"><Button variant="secondary" onClick={onClose}>Cancelar</Button><Button variant="danger" icon={Trash2} busy={busy} onClick={async () => { if (await mutate(path, 'DELETE', {})) onClose(); }}>Eliminar</Button></div></Modal>;
}
