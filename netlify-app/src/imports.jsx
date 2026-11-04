import React, { useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CloudUpload, FileText, Sparkles, Save, Plus, Trash2, CheckCircle2 } from 'lucide-react';
import { useApp, LABELS } from './context.jsx';
import { request } from './api.mjs';
import { PageTitle, Field, Button, ErrorMessage, IconButton } from './ui.jsx';
import { readDocument } from './document-reader.mjs';
import { DAY_NAMES } from '../server/domain.mjs';

export function ImportPage() {
  const [params] = useSearchParams();
  const { session, mutate, busy, setNotice } = useApp();
  const [kind, setKind] = useState(params.get('tipo') === 'horario' ? 'horario' : 'silabo');
  const [reading, setReading] = useState(false), [progress, setProgress] = useState(''), [error, setError] = useState(''), [dragging, setDragging] = useState(false);
  const [drafts, setDrafts] = useState([]), [selected, setSelected] = useState(0);
  const input = useRef(); const active = drafts[selected];
  const patch = change => setDrafts(values => values.map((d, i) => i === selected ? { ...d, ...change } : d));
  const updateRow = (list, index, key, value) => patch({ [list]: active[list].map((r, i) => i === index ? { ...r, [key]: value } : r) });
  const files = async fileList => {
    const list = [...fileList]; if (!list.length || reading) return;
    if (list.length > 5) { setError('Elige como maximo cinco documentos por lote.'); return; }
    setError(''); setReading(true); const results = []; const errors = [];
    for (let i = 0; i < list.length; i++) {
      setProgress(`${i + 1} de ${list.length} · ${list[i].name}`);
      try {
        const body = await readDocument(list[i], kind);
        const result = await request('/import/analyze', { method: 'POST', data: body, csrfToken: session.csrfToken, signal: AbortSignal.timeout(65000) });
        results.push({ ...result, kind, filename: list[i].name });
      } catch (e) { errors.push(`${list[i].name}: ${e.name === 'TimeoutError' ? 'La lectura tardo demasiado. Intenta de nuevo.' : e.message}`); }
    }
    setDrafts(previous => [...previous, ...results]); setError(errors.join('\n')); setReading(false); setProgress(''); if (input.current) input.current.value = '';
  };
  const save = async event => {
    event.preventDefault();
    const cleanCourse = course => ({ ...course, code: course.code.trim().toUpperCase(), topics: course.topics.map(t => t.trim()).filter(Boolean) });
    const payload = { kind: active.kind, ...(active.kind === 'silabo' ? {
      course: cleanCourse(active.course),
      evaluations: active.evaluations.map(e => ({ ...e, time: e.time || null, estimatedHours: null, grade: null, notes: '', completed: false, topics: e.topics || [] }))
    } : { courses: active.courses.map(cleanCourse), classes: active.classes.map(cl => ({ ...cl, code: cl.code.trim().toUpperCase() })) }) };
    if (await mutate('/import/save', 'POST', {}, payload)) {
      setDrafts(values => values.filter((_, i) => i !== selected)); setSelected(0); setNotice({ message: 'Importación guardada. Tu plan se ha actualizado.' });
    }
  };
  const changeCourse = (index, key, value) => {
    if (active.kind === 'silabo') patch({ course: { ...active.course, [key]: value } });
    else {
      const old = active.courses[index].code;
      patch({ courses: active.courses.map((c, i) => i === index ? { ...c, [key]: value } : c), ...(key === 'code' ? { classes: active.classes.map(cl => cl.code === old ? { ...cl, code: value } : cl) } : {}) });
    }
  };
  const courseFields = (course, index) => <div className="import-course" key={index}><div className="form-grid"><Field label="Código"><input value={course.code} maxLength={24} required onChange={e => changeCourse(index, 'code', e.target.value.toUpperCase())} /></Field><Field label="Nombre del curso"><input value={course.name} maxLength={160} required onChange={e => changeCourse(index, 'name', e.target.value)} /></Field><Field label="Créditos"><input type="number" min="0.5" max="20" step="0.5" value={course.credits} required onChange={e => changeCourse(index, 'credits', Number(e.target.value))} /></Field><Field label="Dificultad"><select value={course.difficulty} onChange={e => changeCourse(index, 'difficulty', Number(e.target.value))}>{[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n}/5</option>)}</select></Field></div>
    <Field label="Temas"><textarea rows="3" value={course.topics.join('\n')} onChange={e => changeCourse(index, 'topics', e.target.value.split('\n'))} /></Field></div>;
  return <><PageTitle eyebrow="De tus documentos a tu semana" title="Subir documentos" />
    <div className="segments import-mode"><button aria-pressed={kind === 'silabo'} disabled={reading} onClick={() => setKind('silabo')}>Sílabo</button><button aria-pressed={kind === 'horario'} disabled={reading} onClick={() => setKind('horario')}>Horario</button></div>
    <ErrorMessage message={error} />
    {!session.provider.configured.length && <div className="alert warning">IA no configurada en el servidor. El registro manual sigue disponible.</div>}
    <section className={`upload-zone ${dragging ? 'dragging' : ''}`} onDragOver={e => { e.preventDefault(); setDragging(true); }} onDragLeave={() => setDragging(false)} onDrop={e => { e.preventDefault(); setDragging(false); if (session.provider.configured.length) files(e.dataTransfer.files); }}>
      <CloudUpload size={40} /><h2>{reading ? 'Leyendo tus documentos…' : kind === 'silabo' ? 'Tus sílabos, aquí' : 'Tu horario, aquí'}</h2>
      <p>{reading ? progress : 'PDF, JPEG, PNG o WebP · hasta 15 MB por archivo'}</p>
      <input ref={input} type="file" accept="application/pdf,image/jpeg,image/png,image/webp" multiple={kind === 'silabo'} disabled={reading || !session.provider.configured.length} onChange={e => files(e.target.files)} aria-label="Documentos para analizar" />
      <Button icon={Sparkles} busy={reading} disabled={!session.provider.configured.length} onClick={() => input.current.click()}>{reading ? 'Analizando' : 'Elegir y analizar'}</Button>
      <small>{session.provider.configured.join(' · ')}</small>
    </section>
    {drafts.length > 0 && <section className="import-review"><div className="section-heading"><h2><CheckCircle2 size={20} />Revisar antes de guardar</h2><span className="muted">{drafts.length} documentos</span></div>
      <div className="tabs draft-tabs">{drafts.map((d, i) => <button key={i} aria-pressed={selected === i} onClick={() => setSelected(i)}><FileText size={15} />{d.filename}</button>)}</div>
      {active && <form onSubmit={save}>{active.warnings?.length > 0 && <div className="alert warning"><ul>{active.warnings.map((w, i) => <li key={i}>{w}</li>)}</ul></div>}
        {(active.kind === 'silabo' ? [active.course] : active.courses).map(courseFields)}
        <h3>{active.kind === 'silabo' ? 'Evaluaciones y entregas' : 'Clases'}</h3>
        <div className="review-rows">{active.kind === 'silabo' ? active.evaluations.map((e, i) => <div className="review-row" key={i}>
          <Field label="Nombre"><input value={e.name} maxLength={160} required onChange={ev => updateRow('evaluations', i, 'name', ev.target.value)} /></Field>
          <Field label="Tipo"><select value={e.type} onChange={ev => updateRow('evaluations', i, 'type', ev.target.value)}>{Object.entries(LABELS).map(([v, t]) => <option key={v} value={v}>{t}</option>)}</select></Field>
          <Field label="Fecha"><input type="date" value={e.date} required onChange={ev => updateRow('evaluations', i, 'date', ev.target.value)} /></Field>
          <Field label="Hora"><input type="time" value={e.time || ''} onChange={ev => updateRow('evaluations', i, 'time', ev.target.value)} /></Field>
          <Field label="Peso final (%)"><input type="number" min="0" max="100" step="0.1" value={e.weight} required onChange={ev => updateRow('evaluations', i, 'weight', Number(ev.target.value))} /></Field>
          <IconButton icon={Trash2} label={`Quitar ${e.name}`} onClick={() => patch({ evaluations: active.evaluations.filter((_, n) => n !== i) })} />
        </div>) : active.classes.map((cl, i) => <div className="review-row" key={i}>
          <Field label="Curso"><select value={cl.code} onChange={e => updateRow('classes', i, 'code', e.target.value)}>{active.courses.map(c => <option key={c.code} value={c.code}>{c.code}</option>)}</select></Field>
          <Field label="Día"><select value={cl.weekday} onChange={e => updateRow('classes', i, 'weekday', Number(e.target.value))}>{DAY_NAMES.map((d, n) => <option key={d} value={n + 1}>{d}</option>)}</select></Field>
          <Field label="Inicio"><input type="time" value={cl.start} required onChange={e => updateRow('classes', i, 'start', e.target.value)} /></Field>
          <Field label="Fin"><input type="time" value={cl.end} required onChange={e => updateRow('classes', i, 'end', e.target.value)} /></Field>
          <Field label="Frecuencia"><select value={cl.frequency} onChange={e => updateRow('classes', i, 'frequency', e.target.value)}><option value="SEMANAL">Semanal</option><option value="A">A</option><option value="B">B</option></select></Field>
          <IconButton icon={Trash2} label="Quitar clase" onClick={() => patch({ classes: active.classes.filter((_, n) => n !== i) })} />
        </div>)}</div>
        <Button type="button" variant="secondary" icon={Plus} onClick={() => active.kind === 'silabo' ? patch({ evaluations: [...active.evaluations, { name: '', type: 'TAREA', date: session.today, time: null, weight: 0, topics: [] }] }) : patch({ classes: [...active.classes, { code: active.courses[0].code, weekday: 1, start: '09:00', end: '11:00', frequency: 'SEMANAL', detail: '' }] })}>Agregar fila</Button>
        <div className="form-actions"><Button variant="secondary" type="button" onClick={() => { setDrafts(values => values.filter((_, i) => i !== selected)); setSelected(0); }}>Descartar documento</Button><Button type="submit" busy={busy} icon={Save}>Confirmar y guardar</Button></div>
      </form>}
    </section>}
  </>;
}
