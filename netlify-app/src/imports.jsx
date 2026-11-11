import React, { useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { CloudUpload, FileText, Sparkles, Save, Plus, Trash2, CheckCircle2 } from 'lucide-react';
import { useApp, LABELS } from './context.jsx';
import { request } from './api.mjs';
import { PageTitle, Field, Button, ErrorMessage, IconButton } from './ui.jsx';
import { readDocument } from './document-reader.mjs';
import { DAY_NAMES } from '../server/domain.mjs';
import { Review } from './render-review.jsx';

export function ImportPage({ initialKind, embedded = false, manual = false }) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { session, mutate, busy, setNotice } = useApp();
  const kind = initialKind || (params.get('tipo') === 'horario' ? 'horario' : 'silabo');
  const [chosen, setChosen] = useState([]);
  const [reading, setReading] = useState(false), [progress, setProgress] = useState(''), [error, setError] = useState(''), [dragging, setDragging] = useState(false);
  const [drafts, setDrafts] = useState(() => manual ? [{ kind: 'silabo', filename: '', course: { code: '', name: '', credits: 3, difficulty: 3, color: '#235B4E', topics: [] }, evaluations: [], classes: [], warnings: [] }] : []), [selected, setSelected] = useState(0);
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
        const analyze = () => request('/import/analyze', { method: 'POST', data: body, csrfToken: session.csrfToken, signal: AbortSignal.timeout(65000) });
        let result;
        try { result = await analyze(); } catch (e) {
          // Plan gratuito de Groq: limite por minuto. Esperar lo que indica el proveedor y reintentar una vez.
          if (!e.providerRateLimited || !(e.retryAfter <= 90)) throw e;
          for (let left = e.retryAfter; left > 0; left--) { setProgress(`${i + 1} de ${list.length} · La IA alcanzó su límite por minuto. Reintento en ${left} s…`); await new Promise(r => setTimeout(r, 1000)); }
          setProgress(`${i + 1} de ${list.length} · ${list[i].name}`);
          result = await analyze();
        }
        const palette = ['#235B4E', '#456A9A', '#8D5B84', '#9C6B35', '#477C7A'];
        (kind === 'silabo' ? [result.course] : result.courses).forEach((c, n) => { c.color = session.state.courses.find(old => old.code === c.code)?.color || palette[(session.state.courses.length + n) % palette.length]; });
        results.push({ ...result, kind, filename: list[i].name });
      } catch (e) { errors.push(`${list[i].name}: ${e.name === 'TimeoutError' ? 'La lectura tardo demasiado. Intenta de nuevo.' : e.message}`); }
    }
    setDrafts(previous => [...previous, ...results]); setError(errors.join('\n')); setReading(false); setProgress(''); setChosen([]); if (input.current) input.current.value = '';
  };
  const save = async event => {
    event.preventDefault();
    const cleanCourse = course => ({ ...course, code: course.code.trim().toUpperCase(), topics: course.topics.map(t => t.trim()).filter(Boolean) });
    const payload = { kind: active.kind, ...(active.kind === 'silabo' ? {
      course: cleanCourse(active.course),
      evaluations: active.evaluations.map(e => ({ ...e, time: e.time || null, estimatedHours: e.estimatedHours || null, grade: null, notes: '', completed: false, topics: e.topics || [] })),
      classes: (active.classes || []).map(({ weekday, start, end }) => ({ weekday, start, end }))
    } : { courses: active.courses.map(cleanCourse), classes: active.classes.map(cl => ({ ...cl, code: cl.code.trim().toUpperCase() })) }) };
    const saved = await mutate('/import/save', 'POST', {}, payload);
    if (saved) {
      // Los cruces del horario se guardan igual (como en Render) y se avisan aquí.
      setDrafts(values => values.filter((_, i) => i !== selected)); setSelected(0); setNotice({ message: ['Importación guardada. Tu plan se ha actualizado.', ...(saved.notes || [])].join(' ') });
      if (drafts.length === 1) navigate(active.kind === 'horario' ? '/horario' : '/cursos');
    }
  };
  const changeCourse = (index, key, value) => {
    if (active.kind === 'silabo') patch({ course: { ...active.course, [key]: value } });
    else {
      const old = active.courses[index].code;
      patch({ courses: active.courses.map((c, i) => i === index ? { ...c, [key]: value } : c), ...(key === 'code' ? { classes: active.classes.map(cl => cl.code === old ? { ...cl, code: value } : cl) } : {}) });
    }
  };
  if (active) return <><ErrorMessage message={error} /><Review active={active} drafts={drafts} selected={selected} setSelected={setSelected} patch={patch} changeCourse={changeCourse} save={save} discard={() => { setDrafts(values => values.filter((_, i) => i !== selected)); setSelected(0); }} busy={busy} /></>;
  const courseFields = (course, index) => <div className="import-course" key={index}><div className="form-grid"><Field label="Código"><input value={course.code} maxLength={24} required onChange={e => changeCourse(index, 'code', e.target.value.toUpperCase())} /></Field><Field label="Nombre del curso"><input value={course.name} maxLength={160} required onChange={e => changeCourse(index, 'name', e.target.value)} /></Field><Field label="Créditos"><input type="number" min="0.5" max="20" step="0.5" value={course.credits} required onChange={e => changeCourse(index, 'credits', Number(e.target.value))} /></Field><Field label="Dificultad"><select value={course.difficulty} onChange={e => changeCourse(index, 'difficulty', Number(e.target.value))}>{[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n}/5</option>)}</select></Field></div>
    <Field label="Temas"><textarea rows="3" value={course.topics.join('\n')} onChange={e => changeCourse(index, 'topics', e.target.value.split('\n'))} /></Field></div>;
  return <>{!embedded && <PageTitle eyebrow="Con inteligencia artificial" title={kind === 'silabo' ? 'Sube tu sílabo' : 'Importar horario'} description={kind === 'silabo' ? 'La IA lee el PDF y encuentra tus evaluaciones, semanas, pesos y temas. Tú revisas y listo.' : 'Sube una foto de tu horario o tu consolidado de matrícula y la IA lo arma por ti.'} />}
    <ErrorMessage message={error} />
    {!session.provider.configured.length && <div className="aviso-dorado">Falta configurar la IA. Mientras tanto, puedes <Link to="/silabos/manual">registrar el curso manualmente</Link>.</div>}
    <div className="row g-4"><section className="col-lg-7"><form className="tarjeta p-4 h-100" onSubmit={e => { e.preventDefault(); files(chosen); }}><label className={`zona-subida ${dragging ? 'arrastrando' : ''}`} onDragOver={e => { e.preventDefault(); setDragging(true); }} onDragLeave={() => setDragging(false)} onDrop={e => { e.preventDefault(); setDragging(false); if (!reading) setChosen([...e.dataTransfer.files]); }}>
      <input ref={input} type="file" accept={kind === 'silabo' ? 'application/pdf,.pdf' : 'application/pdf,image/jpeg,image/png,image/webp'} multiple={kind === 'silabo'} disabled={reading || !session.provider.configured.length} onChange={e => setChosen([...e.target.files])} aria-label="Documentos para analizar" />
      <span className={`zona-icono ${kind === 'horario' ? 'zona-icono-horario' : ''}`}><i className={`bi bi-${kind === 'horario' ? 'calendar-week' : 'file-earmark-pdf'}`} /></span><strong>{chosen.length ? chosen.map(f => f.name).join(' · ') : kind === 'horario' ? 'Arrastra la foto de tu horario aquí' : 'Arrastra tu sílabo aquí'}</strong><span className="text-body-secondary small">{kind === 'horario' ? 'o haz clic para elegir · JPG, PNG, WEBP o PDF (máx. 15 MB)' : 'o haz clic para elegir un PDF (máx. 15 MB)'}</span>
    </label><div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mt-4"><small className="text-body-secondary"><i className="bi bi-cpu me-1" />{session.provider.configured.join(' · ')}</small><Button type="submit" icon={Sparkles} busy={reading} disabled={!chosen.length || !session.provider.configured.length}>{kind === 'horario' ? 'Leer horario con IA' : 'Analizar con IA'}</Button></div></form>{kind === 'silabo' && <p className="small text-body-secondary mt-3 mb-0">¿Prefieres hacerlo tú? <Link to="/silabos/manual">Registra el curso manualmente</Link>.</p>}</section><section className="col-lg-5"><div className="tarjeta h-100"><div className="tarjeta-cabecera"><h2 className="tarjeta-titulo"><span className="icono-titulo"><i className={`bi bi-${kind === 'horario' ? 'lightbulb' : 'magic'}`} /></span>{kind === 'horario' ? 'Qué puedes subir' : 'Cómo funciona'}</h2></div><div className="tarjeta-cuerpo">{kind === 'horario' ? <ul className="lista-consejos"><li><i className="bi bi-phone" /><span><strong>Captura o foto</strong> de tu horario semanal (Canvas, intranet o impreso).</span></li><li><i className="bi bi-file-earmark-pdf" /><span><strong>Consolidado o constancia de matrícula</strong> en PDF.</span></li><li><i className="bi bi-brightness-high" /><span>Que se lea bien: buena luz, sin cortes ni reflejos.</span></li><li><i className="bi bi-palette" /><span>Cada curso recibe su propio color; si ya existía, conserva el suyo y sus evaluaciones.</span></li></ul> : <ol className="pasos"><li><strong>Sube el PDF</strong><p>El sílabo oficial de tu curso.</p></li><li><strong>Revisa lo que encontró la IA</strong><p>Evaluaciones, semanas, pesos y temas. Puedes corregir todo.</p></li><li><strong>Guarda y empieza</strong><p>Tu calendario y tu plan de estudio se actualizan automáticamente.</p></li></ol>}</div></div></section></div>
    {reading && <div className="cargando" aria-live="polite"><div className="cargando-tarjeta"><div className="cargando-anillo" /><strong>{kind === 'horario' ? 'Leyendo tu horario…' : 'Analizando tu sílabo…'}</strong><span>{progress}</span><span>La IA está identificando tus cursos, días y horas. Suele tardar entre 10 y 40 segundos.</span></div></div>}
    {drafts.length > 0 && <section className="import-review"><div className="section-heading"><h2><CheckCircle2 size={20} />Revisar antes de guardar</h2><span className="muted">{drafts.length} documentos</span></div>
      <div className="tabs draft-tabs">{drafts.map((d, i) => <button key={i} aria-pressed={selected === i} onClick={() => setSelected(i)}><FileText size={15} />{d.filename}</button>)}</div>
      {active && <form className="tarjeta p-4" onSubmit={save}>{active.warnings?.length > 0 && <div className="aviso-dorado"><ul>{active.warnings.map((w, i) => <li key={i}>{w}</li>)}</ul></div>}
        {(active.kind === 'silabo' ? [active.course] : active.courses).map(courseFields)}
        <h3>{active.kind === 'silabo' ? 'Evaluaciones y entregas' : 'Clases'}</h3>
        <div className="review-rows">{active.kind === 'silabo' ? active.evaluations.map((e, i) => <div className="review-row" key={i}>
          <Field label="Nombre"><input value={e.name} maxLength={160} required onChange={ev => updateRow('evaluations', i, 'name', ev.target.value)} /></Field>
          <Field label="Tipo"><select value={e.type} onChange={ev => updateRow('evaluations', i, 'type', ev.target.value)}>{Object.entries(LABELS).map(([v, t]) => <option key={v} value={v}>{t}</option>)}</select></Field>
          <Field label="Fecha"><input type="date" value={e.date} required onChange={ev => updateRow('evaluations', i, 'date', ev.target.value)} /></Field>
          <Field label="Hora"><input type="time" value={e.time || ''} onChange={ev => updateRow('evaluations', i, 'time', ev.target.value)} /></Field>
          <Field label="Peso final (%)"><input type="number" min="0" max="100" step="any" value={e.weight} required onChange={ev => updateRow('evaluations', i, 'weight', Number(ev.target.value))} /></Field>
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
