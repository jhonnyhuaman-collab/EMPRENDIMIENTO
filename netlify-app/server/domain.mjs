import { z } from 'zod';

export const TYPES = ['PRACTICA', 'EXAMEN', 'LABORATORIO', 'PROYECTO', 'TAREA'];
export const DAY_NAMES = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
export const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine(value => {
  const date = new Date(value + 'T00:00:00Z');
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}, 'La fecha no es válida.');
export const time = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'La hora no es válida.');
const text = (min, max) => z.string().trim().min(min).max(max);
export const settingsSchema = z.object({
  name: text(1, 100), cycleStart: isoDate.refine(d => weekday(d) === 1, 'El inicio del ciclo debe ser lunes.'),
  dailyHours: z.number().min(.5).max(12), totalDailyHours: z.number().min(3).max(16),
  weeklyCourseHours: z.number().min(1).max(20),
  studyDays: z.array(z.number().int().min(1).max(7)).min(1).max(7).refine(a => new Set(a).size === a.length),
  sleepTime: time
});
export const courseSchema = z.object({
  code: text(1, 24).transform(s => s.toUpperCase()), name: text(1, 160),
  credits: z.number().min(.5).max(20), difficulty: z.number().int().min(1).max(5),
  color: z.string().regex(/^#[a-f\d]{6}$/i), topics: z.array(text(1, 300)).max(100).default([]),
  topicSchedule: z.array(z.object({ title: text(1, 300), week: z.number().int().min(1).max(16) })).max(100).default([])
});
export const evaluationSchema = z.object({
  courseId: z.uuid(), name: text(1, 160), type: z.enum(TYPES), date: isoDate,
  time: time.nullable().default(null), weight: z.number().min(0).max(100),
  estimatedHours: z.number().min(.5).max(200).nullable().default(null),
  grade: z.number().min(0).max(20).nullable().default(null),
  notes: text(0, 4000).default(''), topics: z.array(text(1, 300)).max(100).default([]),
  completed: z.boolean().default(false),
  origin: z.enum(['AGENDA', 'SILABO']).default('AGENDA'), week: z.number().int().min(1).max(52).nullable().default(null)
});
export const classSchema = z.object({
  courseId: z.uuid(), weekday: z.number().int().min(1).max(7), start: time, end: time,
  frequency: z.enum(['SEMANAL', 'A', 'B']).default('SEMANAL'), detail: text(0, 200).default('')
}).refine(c => c.end > c.start, 'La hora final debe ser posterior al inicio.');

export function initialDocument(email) {
  const local = email.split('@')[0].replace(/[._+\-\d]+/g, ' ').trim();
  const name = local ? local.split(/\s+/).slice(0, 3).map(s => s[0].toUpperCase() + s.slice(1)).join(' ') : 'Estudiante';
  return {
    settings: { name, cycleStart: '2026-08-10', dailyHours: 3, totalDailyHours: 9, weeklyCourseHours: 4, studyDays: [1, 2, 3, 4, 5], sleepTime: '23:00' },
    courses: [], evaluations: [], classes: [], blocks: [], lastPlanDate: null, warnings: []
  };
}
export function limaNow(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(now);
  const p = Object.fromEntries(parts.map(p => [p.type, p.value]));
  return { date: `${p.year}-${p.month}-${p.day}`, minutes: Number(p.hour) * 60 + Number(p.minute) };
}
export const addDays = (date, days) => new Date(Date.parse(date + 'T00:00:00Z') + days * 86400000).toISOString().slice(0, 10);
export const daysBetween = (a, b) => Math.round((Date.parse(b + 'T00:00:00Z') - Date.parse(a + 'T00:00:00Z')) / 86400000);
export const weekday = date => new Date(date + 'T00:00:00Z').getUTCDay() || 7;
export const academicWeek = (start, date) => Math.floor(daysBetween(start, date) / 7) + 1;
export const monday = date => addDays(date, 1 - weekday(date));
export const minutes = value => Number(value.slice(0, 2)) * 60 + Number(value.slice(3, 5));
export const clock = value => String(Math.floor(value / 60)).padStart(2, '0') + ':' + String(value % 60).padStart(2, '0');
export function applies(cl, date, cycleStart) {
  const week = academicWeek(cycleStart, date);
  return cl.weekday === weekday(date) && (cl.frequency === 'SEMANAL' || (cl.frequency === 'A' ? week % 2 !== 0 : week % 2 === 0));
}
export function estimatedHours(type, difficulty, weight) {
  const base = { EXAMEN: 10, PROYECTO: 12, PRACTICA: 5, TAREA: 4, LABORATORIO: 3 }[type];
  return Math.max(.5, Math.round(base * (.6 + .2 * difficulty) * (weight >= 20 ? 1.2 : 1) * 2) / 2);
}
export function freeSlots(date, from, settings, classes, blocks) {
  const sleep = minutes(settings.sleepTime);
  const end = sleep < 720 ? 1380 : sleep - 60;
  const busy = [
    ...classes.filter(c => applies(c, date, settings.cycleStart)).map(c => [minutes(c.start), minutes(c.end) + 15]),
    ...blocks.filter(b => b.date === date && !b.reprogrammed).map(b => [minutes(b.start), minutes(b.end) + 15])
  ].sort((a, b) => a[0] - b[0]);
  let cursor = Math.max(480, from || 0);
  const free = [];
  for (const [a, b] of busy) {
    if (Math.min(a, end) - cursor >= 30) free.push([cursor, Math.min(a, end)]);
    cursor = Math.max(cursor, b);
  }
  if (end - cursor >= 30) free.push([cursor, end]);
  return free;
}
