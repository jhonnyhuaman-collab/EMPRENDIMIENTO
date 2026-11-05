import { addDays, daysBetween, weekday, academicWeek } from './domain.mjs';
import { reviewPlan } from './review.mjs';

const order = new Intl.Collator('es', { numeric: true, sensitivity: 'base' });
const maxDate = (...dates) => dates.reduce((a, b) => a > b ? a : b);
const key = text => text.trim().match(/^\d+(?:\.\d+)*/)?.[0];
const studyDates = (start, end, days) => {
  const dates = []; for (let d = start; d < end; d = addDays(d, 1)) if (days.includes(weekday(d))) dates.push(d); return dates;
};

export function orderedTopics(e, course) {
  const source = e.topics?.length ? e.topics : course.topics || [];
  return [...new Set(source.map(t => {
    const code = key(t);
    return code ? course.topics?.find(candidate => key(candidate) === code) || t.trim() : t.trim();
  }).filter(Boolean))].sort(order.compare);
}

// El presupuesto de pasos suma exactamente las horas: muchos temas comparten
// una sesion cuando no hay media hora disponible para cada uno.
export function semesterSteps(e, course, start, end, settings) {
  const slots = Math.max(1, Math.round(e.estimatedHours * 2));
  const topics = orderedTopics(e, course);
  const exam = ['EXAMEN', 'PRACTICA'].includes(e.type);
  const finalSlots = exam && slots > 1 ? Math.max(1, Math.round(slots * .2)) : 0;
  const contentSlots = slots - finalSlots;
  const schedule = course.topicSchedule || [];
  const latestWeek = Math.min(16, Math.max(1, academicWeek(settings.cycleStart, addDays(end, exam ? -8 : -1))));
  const firstWeek = Math.min(latestWeek, Math.max(1, academicWeek(settings.cycleStart, start)));
  const steps = [];
  if (topics.length) {
    const groups = Math.min(topics.length, contentSlots);
    for (let i = 0; i < groups; i++) {
      const from = Math.floor(i * topics.length / groups), to = Math.floor((i + 1) * topics.length / groups);
      const group = topics.slice(from, to);
      const weeks = group.map((title, j) => {
        const actual = schedule.find(t => t.title === title || key(t.title) && key(t.title) === key(title));
        if (actual) return actual.week;
        // Propuesta de estudio, no se afirma que sea la semana de clase.
        return firstWeek + Math.floor((from + j) * Math.max(1, latestWeek - firstWeek) / topics.length);
      });
      const release = maxDate(start, addDays(settings.cycleStart, (Math.max(...weeks) - 1) * 7));
      steps.push({ title: group.join(' · '), topics: group, hours: (Math.floor(contentSlots / groups) + (i < contentSlots % groups ? 1 : 0)) / 2,
        release, detail: 'Comprender el tema, practicar ejercicios y revisar errores', phase: 'tema' });
    }
  } else {
    const generic = reviewPlan({ ...e, topics: [] });
    const count = Math.min(contentSlots, generic.length);
    for (let i = 0; i < count; i++) steps.push({ title: generic[i].title, topics: [], hours: (Math.floor(contentSlots / count) + (i < contentSlots % count ? 1 : 0)) / 2, release: start, detail: generic[i].detail, phase: 'practica' });
  }
  if (finalSlots) steps.push({ title: 'Repaso final y simulacro', topics, hours: finalSlots / 2, release: maxDate(start, addDays(end, -7)), detail: 'Recordar sin apuntes, resolver un examen con tiempo y corregir errores', phase: 'simulacro' });
  return steps;
}

export function semesterStrategy(e, course, evaluations, settings, today) {
  const cycleEnd = addDays(settings.cycleStart, 16 * 7);
  const end = e.date < cycleEnd ? e.date : cycleEnd;
  const sustained = ['EXAMEN', 'PROYECTO'].includes(e.type);
  let start = settings.cycleStart;
  if (sustained) {
    const previous = evaluations.filter(other => other.id !== e.id && other.courseId === e.courseId && other.type === 'EXAMEN' && other.date < e.date).map(other => other.date).sort().at(-1);
    if (previous) start = maxDate(start, addDays(previous, 1));
  } else {
    let days = Math.min(14, Math.max(3, Math.ceil(e.estimatedHours / 1.5) + 1)); start = end;
    while (days) { start = addDays(start, -1); if (settings.studyDays.includes(weekday(start))) days--; }
    start = maxDate(start, settings.cycleStart);
  }
  start = maxDate(start, today);
  const steps = semesterSteps(e, course, start, end, settings);
  return { start, end, steps, sustained, topicsMissing: !orderedTopics(e, course).length,
    proposedWeeks: orderedTopics(e, course).some(title => !(course.topicSchedule || []).some(t => t.title === title || key(t.title) && key(t.title) === key(title))) };
}

// Acumular pequeñas cuotas a lo largo del periodo, sin volcar el atraso en hoy.
// El saldo se calcula desde hoy: nunca se generan bloques en semanas pasadas.
export function semesterQuota(e, date, settings) {
  const final = e.steps.find(s => s.phase === 'simulacro');
  const content = e.steps.filter(s => s.phase !== 'simulacro');
  const contentHours = content.reduce((sum, s) => sum + s.hours, 0);
  const contentDone = Math.min(contentHours, e.initialConsumed);
  const finalDone = Math.max(0, e.initialConsumed - contentHours);
  const targetFor = (hours, done, release, end) => {
    const dates = studyDates(release, end, settings.studyDays);
    const elapsed = dates.filter(d => d <= date).length;
    return done + (date >= release && dates.length ? Math.ceil(Math.max(0, hours - done) * 2 * elapsed / dates.length) / 2 : 0);
  };
  const preferredEnd = final?.release || e.end;
  const contentEnd = e.start >= preferredEnd || content.some(s => s.release >= preferredEnd) ? e.end : preferredEnd;
  const target = targetFor(contentHours, contentDone, e.start, contentEnd) + (final ? targetFor(final.hours, finalDone, maxDate(e.start, final.release), e.end) : 0);
  const allowed = Math.max(0, target - e.consumed);
  // Respetar el orden: no saltar a un tema que aun no se ha liberado.
  let cumulative = 0;
  for (const step of e.steps) {
    if (step.release > date) break;
    cumulative += step.hours;
  }
  return Math.max(0, Math.min(allowed, cumulative - e.consumed, e.remaining, ['EXAMEN', 'PROYECTO'].includes(e.type) ? 3 : 2));
}

export function nextSegment(e, date, maxHours) {
  let offset = 0;
  for (const step of e.steps) {
    const end = offset + step.hours;
    if (e.consumed < end - 1e-8) return step.release <= date ? { step, hours: Math.min(maxHours, end - e.consumed) } : null;
    offset = end;
  }
  return null;
}
