// Conservar filas y columnas de tablas, aunque PDF.js entregue objetos fuera de orden.
export function pageText(items) {
  const words = items.filter(item => typeof item.str === 'string' && item.str.trim() && Array.isArray(item.transform))
    .map(item => ({ text: item.str.trim(), x: item.transform[4], y: item.transform[5], width: Math.max(0, item.width || 0) }))
    .filter(item => Number.isFinite(item.x) && Number.isFinite(item.y)).sort((a, b) => b.y - a.y || a.x - b.x);
  const rows = [];
  for (const word of words) {
    const last = rows.at(-1);
    if (last && Math.abs(last.y - word.y) <= 3) last.words.push(word);
    else rows.push({ y: word.y, words: [word] });
  }
  return rows.map(row => {
    const parts = row.words.sort((a, b) => a.x - b.x); let end = null;
    return parts.map(word => { const separator = end === null ? '' : word.x - end > 16 ? ' | ' : ' '; end = word.x + word.width; return separator + word.text; }).join('');
  }).join('\n');
}

// Orden original del PDF, celda por celda (como PDFBox en la version Render). En un consolidado
// de matricula cada curso queda seguido de sus propias sesiones; la reconstruccion por filas de
// pageText mezcla sesiones de cursos vecinos porque las celdas estan centradas verticalmente.
export function streamText(items) {
  return items.filter(item => typeof item.str === 'string')
    .map(item => item.str + (item.hasEOL ? '\n' : ''))
    .join('').replace(/[ \t]+\n/g, '\n').replace(/\n{2,}/g, '\n').trim();
}
