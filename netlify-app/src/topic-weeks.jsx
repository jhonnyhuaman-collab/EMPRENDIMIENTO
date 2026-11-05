import React from 'react';
export function TopicWeeks({ topics, schedule = [], onChange }) {
  const update = (title, value) => onChange([...schedule.filter(t => t.title !== title), ...(value ? [{ title, week: Number(value) }] : [])]);
  if (!topics.filter(Boolean).length) return null;
  return <div className="mt-3"><h3 className="seccion-mini">Semana de cada tema</h3><p className="small text-body-secondary">Usa la semana del sílabo. Si no se indica, el plan propone su distribución en orden y reserva el repaso final.</p><div className="table-responsive"><table className="table tabla-edicion align-middle"><thead><tr><th>Tema</th><th>Semana</th></tr></thead><tbody>{[...new Set(topics.filter(Boolean))].map((title, i) => <tr key={title}><td>{title}</td><td><select className="form-select campo" aria-label={`Semana del tema ${i + 1}`} value={schedule.find(t => t.title === title)?.week || ''} onChange={e => update(title, e.target.value)}><option value="">Proponer en el plan</option>{Array.from({ length: 16 }, (_, n) => <option key={n} value={n + 1}>Semana {n + 1}</option>)}</select></td></tr>)}</tbody></table></div></div>;
}
