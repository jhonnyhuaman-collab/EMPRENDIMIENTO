import { addDays, weekday, daysBetween } from './domain.mjs';

// Port de PlanRepasoService, PrioridadService y VentanaPreparacionService de Java.
export function reviewPlan(e) {
  const round = n => Math.max(.5, Math.round(n * 2) / 2), hours = e.estimatedHours || 0;
  if (e.topics.length) return [...e.topics.map(title => ({ title, detail: 'Repasar teoría y resolver ejercicios del tema', hours: round(hours * .8 / e.topics.length) })), { title: 'Simulacro final', detail: 'Resolver evaluaciones pasadas con tiempo controlado', hours: round(hours * .2) }];
  const steps = {
    LABORATORIO: [['Leer la guía', 'Revisar objetivos y fundamento teórico', .2], ['Preparar la práctica', 'Simulación, código o montaje previo', .5], ['Redactar el informe', 'Resultados, análisis y conclusiones', .3]],
    PROYECTO: [['Definir alcance', 'Objetivos, entregables y reparto de tareas', .2], ['Desarrollo del proyecto', 'Avanzar el producto del proyecto', .6], ['Documento y exposición', 'Informe final y ensayo de la presentación', .2]],
    TAREA: [['Entender el enunciado', 'Identificar qué se pide y cómo se califica', .2], ['Resolver la tarea', 'Desarrollar la tarea', .6], ['Revisar y entregar', 'Corregir formato y subir a tiempo', .2]]
  }[e.type] || [['Repasar teoría', 'Apuntes, diapositivas y resúmenes', .5], ['Resolver ejercicios', 'Problemas tipo de clase', .3], ['Simulacro final', 'Evaluaciones pasadas con tiempo controlado', .2]];
  return steps.map(([title, detail, fraction]) => ({ title, detail, hours: round(hours * fraction) }));
}
export function recommendedStart(e, studyDays) {
  let remaining = Math.max(3, Math.min(14, Math.ceil((e.estimatedHours || 0) / 1.5) + 1)), date = e.date;
  const days = studyDays.length ? studyDays : [1, 2, 3, 4, 5, 6, 7];
  while (remaining) { date = addDays(date, -1); if (days.includes(weekday(date))) remaining--; }
  return date;
}
export function priority(e, course, today) {
  const score = 100 * (.5 * (1 - Math.min(30, Math.max(0, daysBetween(today, e.date))) / 30) + .3 * Math.min(40, e.weight || 0) / 40 + .2 * course.difficulty / 5);
  return score >= 65 ? 'alta' : score >= 45 ? 'media' : 'baja';
}

export function alignSyllabusDates(doc) {
  for (const e of doc.evaluations) {
    if (e.origin !== 'SILABO' || !e.week) continue;
    const sessions = doc.classes.filter(c => c.courseId === e.courseId).sort((a, b) => a.weekday - b.weekday || a.start.localeCompare(b.start));
    const weekly = sessions.filter(c => c.frequency === 'SEMANAL' || c.frequency === (e.week % 2 ? 'A' : 'B'));
    const desired = list => list.find(c => /lab/i.test(c.detail) === (e.type === 'LABORATORIO'));
    const session = desired(weekly) || desired(sessions) || weekly[0] || sessions[0];
    if (session) { e.date = addDays(doc.settings.cycleStart, (e.week - 1) * 7 + session.weekday - 1); e.time = session.start; }
  }
}
