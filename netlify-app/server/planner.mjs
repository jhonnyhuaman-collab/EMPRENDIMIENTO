import { randomUUID } from 'node:crypto';
import { addDays, daysBetween, monday, weekday, minutes, clock, applies, freeSlots, estimatedHours } from './domain.mjs';

const half = n => Math.floor((n + 1e-8) * 2) / 2;
const duration = b => (minutes(b.end) - minutes(b.start)) / 60;

export function generatePlan(document, now) {
  const doc = structuredClone(document);
  const { settings, courses, evaluations, classes } = doc;
  const today = now.date;
  const blocks = doc.blocks.filter(b => b.completed || b.date < today);
  for (const block of blocks) if (!block.completed && block.date < today) block.reprogrammed = true;
  const pending = evaluations.filter(e => !e.completed && e.date > today).map(e => {
    const course = courses.find(c => c.id === e.courseId);
    const hours = e.estimatedHours ?? estimatedHours(e.type, course.difficulty, e.weight);
    const done = blocks.filter(b => b.evaluationId === e.id && b.completed).reduce((s, b) => s + duration(b), 0);
    let prepDays = Math.min(14, Math.max(3, Math.ceil(hours / 1.5) + 1));
    let start = e.date;
    while (prepDays) { start = addDays(start, -1); if (settings.studyDays.includes(weekday(start))) prepDays--; }
    return { ...e, course, remaining: Math.max(0, hours - done), start };
  });
  const end = pending.reduce((last, e) => e.date > last ? e.date : last, today);
  const horizon = addDays(today, 366);
  const weeks = new Map();
  for (const block of blocks.filter(b => !b.reprogrammed && b.date >= monday(today))) {
    const ev = evaluations.find(e => e.id === block.evaluationId);
    if (ev) { const key = `${ev.courseId}:${monday(block.date)}`; weeks.set(key, (weeks.get(key) || 0) + duration(block)); }
  }
  for (let date = today; date < end && date < horizon; date = addDays(date, 1)) {
    if (!settings.studyDays.includes(weekday(date))) continue;
    const dayClasses = classes.filter(c => applies(c, date, settings.cycleStart));
    const intervals = dayClasses.map(c => [minutes(c.start), minutes(c.end)]).sort((a, b) => a[0] - b[0]);
    let covered = 0, classMinutes = 0;
    for (const [start, finish] of intervals) { classMinutes += Math.max(0, finish - Math.max(start, covered)); covered = Math.max(covered, finish); }
    const occupied = blocks.filter(b => b.date === date && !b.reprogrammed);
    let capacity = half(Math.min(settings.dailyHours, Math.max(1, settings.totalDailyHours - classMinutes / 60)) - occupied.reduce((s, b) => s + duration(b), 0));
    const candidates = pending.filter(e => e.remaining >= .5 && e.start <= date && date < e.date)
      .sort((a, b) => score(b, date) - score(a, date) || a.id.localeCompare(b.id));
    const allocations = new Map();
    const quota = new Map(candidates.map(e => {
      let days = 0;
      for (let d = date; d < e.date; d = addDays(d, 1)) if (settings.studyDays.includes(weekday(d))) days++;
      return [e.id, Math.min(['EXAMEN', 'PROYECTO'].includes(e.type) ? 3 : 2, Math.max(Math.min(1, e.remaining), Math.ceil(e.remaining / Math.max(1, days) * 2) / 2))];
    }));
    const courseAllocations = new Map();
    for (const round of [1, .5, 20]) {
      for (const e of candidates) {
        const allocated = allocations.get(e.id) || 0;
        const weekKey = `${e.courseId}:${monday(date)}`;
        const courseUsed = courseAllocations.get(e.courseId) || 0;
        const hours = half(Math.min(round, quota.get(e.id) - allocated, e.remaining - allocated, capacity,
          settings.weeklyCourseHours - (weeks.get(weekKey) || 0) - courseUsed));
        if (hours < .5) continue;
        allocations.set(e.id, allocated + hours);
        courseAllocations.set(e.courseId, courseUsed + hours);
        capacity -= hours;
      }
    }
    for (const e of candidates) {
      let requested = allocations.get(e.id) || 0;
      const from = date === today ? Math.ceil(now.minutes / 30) * 30 : 480;
      // Preferir estudiar despues de la ultima clase, sin renunciar a huecos disponibles.
      const slots = freeSlots(date, from, settings, classes, blocks);
      const lastClass = Math.max(0, ...dayClasses.map(c => minutes(c.end) + 15));
      const ordered = [...slots.filter(([a]) => a >= lastClass), ...slots.filter(([a]) => a < lastClass)];
      for (const [a, b] of ordered) {
        let cursor = a;
        while (requested >= .5 && b - cursor >= 30) {
          const count = Math.min(90, Math.floor(Math.min(requested * 60, b - cursor) / 30) * 30);
          if (count < 30) break;
          const done = blocks.filter(block => block.evaluationId === e.id && block.completed).length;
          const topic = e.topics?.length ? e.topics[(done + blocks.filter(block => block.evaluationId === e.id && !block.completed).length) % e.topics.length] : 'Repaso y ejercicios';
          blocks.push({ id: randomUUID(), evaluationId: e.id, date, start: clock(cursor), end: clock(cursor + count), description: `${e.name}: ${topic}`, completed: false, reprogrammed: false });
          requested -= count / 60;
          e.remaining -= count / 60;
          const key = `${e.courseId}:${monday(date)}`;
          weeks.set(key, (weeks.get(key) || 0) + count / 60);
          cursor += count + 15;
        }
        if (requested < .5) break;
      }
    }
  }
  doc.blocks = blocks;
  doc.lastPlanDate = today;
  doc.warnings = pending.filter(e => e.remaining >= .5).map(e => `${e.course.code} · ${e.name}: faltan ${e.remaining.toFixed(1)} h por planificar. Revisa tus limites y dias de estudio.`);
  return doc;
}

export function score(e, date) {
  return 100 * (.5 * (1 - Math.min(30, Math.max(0, daysBetween(date, e.date))) / 30) + .3 * Math.min(40, e.weight) / 40 + .2 * e.course.difficulty / 5);
}

const escape = value => String(value).replace(/\\/g, '\\\\').replace(/\r?\n/g, '\\n').replace(/,/g, '\\,').replace(/;/g, '\\;');
function fold(line) {
  let out = '', bytes = 0;
  for (const char of line) {
    const count = Buffer.byteLength(char);
    if (bytes + count > 75) { out += '\r\n '; bytes = 1; }
    out += char; bytes += count;
  }
  return out;
}
export function calendarIcs(doc, now = new Date()) {
  const stamp = now.toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, 'Z');
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Planificador Academico//ES', 'CALSCALE:GREGORIAN',
    'BEGIN:VTIMEZONE', 'TZID:America/Lima', 'BEGIN:STANDARD', 'DTSTART:19700101T000000', 'TZOFFSETFROM:-0500', 'TZOFFSETTO:-0500', 'END:STANDARD', 'END:VTIMEZONE'];
  const add = (id, name, date, start, end, description) => {
    lines.push('BEGIN:VEVENT', `UID:${id}@planificador`, `DTSTAMP:${stamp}`, `SUMMARY:${escape(name)}`);
    if (!start) lines.push(`DTSTART;VALUE=DATE:${date.replaceAll('-', '')}`, `DTEND;VALUE=DATE:${addDays(date, 1).replaceAll('-', '')}`);
    else {
      const t = (time, day = date) => `${day.replaceAll('-', '')}T${time.replace(':', '')}00`;
      const finish = end || clock((minutes(start) + 60) % 1440);
      const finishDate = !end && minutes(start) + 60 >= 1440 ? addDays(date, 1) : date;
      lines.push(`DTSTART;TZID=America/Lima:${t(start)}`, `DTEND;TZID=America/Lima:${t(finish, finishDate)}`);
    }
    lines.push(`DESCRIPTION:${escape(description || '')}`, 'END:VEVENT');
  };
  for (const e of doc.evaluations.filter(e => !e.completed)) {
    const c = doc.courses.find(c => c.id === e.courseId);
    add(e.id, `${c?.code || ''} · ${e.name}`, e.date, e.time, null, e.notes);
  }
  for (const b of doc.blocks.filter(b => !b.reprogrammed && !b.completed)) add(b.id, b.description, b.date, b.start, b.end);
  lines.push('END:VCALENDAR');
  return lines.map(fold).join('\r\n') + '\r\n';
}
