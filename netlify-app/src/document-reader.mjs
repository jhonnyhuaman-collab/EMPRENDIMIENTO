import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist/legacy/build/pdf.mjs';
import workerUrl from 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url';
import { pageText, streamText } from './pdf-layout.mjs';
GlobalWorkerOptions.workerSrc = workerUrl;

export async function readDocument(file, kind) {
  if (file.size > 15 * 1024 * 1024) throw new Error('El archivo supera los 15 MB.');
  if (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) {
    const bytes = new Uint8Array(await file.arrayBuffer());
    if (new TextDecoder().decode(bytes.slice(0, 5)) !== '%PDF-') throw new Error('El archivo no es un PDF valido.');
    const loadingTask = getDocument({ data: bytes, isEvalSupported: false });
    try {
      const pdf = await loadingTask.promise;
      if (pdf.numPages > 150) throw new Error('El PDF tiene mas de 150 paginas. Divide el documento.');
      let text = '';
      for (let p = 1; p <= pdf.numPages; p++) {
        const page = await pdf.getPage(p); const content = await page.getTextContent();
        text += `\n--- Pagina ${p} ---\n`;
        // Horario: orden original del PDF, como Render. Silabo: filas y columnas de tablas.
        text += (kind === 'horario' ? streamText(content.items) : pageText(content.items)) + '\n';
        if (text.length > 150000) throw new Error('El documento tiene demasiado texto. Divide el PDF antes de analizarlo.');
        page.cleanup();
      }
      if (text.replace(/--- Pagina \d+ ---/g, '').trim().length < 30) throw new Error('Este PDF parece escaneado y no contiene texto legible. Usa un PDF con texto o una imagen clara.');
      return { kind, text };
    } finally { await loadingTask.destroy(); }
  }
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new Error('Elige un PDF o una imagen JPEG, PNG o WebP.');
  const image = await createImageBitmap(file);
  try {
    const ratio = Math.min(1, 1700 / Math.max(image.width, image.height));
    const canvas = document.createElement('canvas'); canvas.width = Math.round(image.width * ratio); canvas.height = Math.round(image.height * ratio);
    const ctx = canvas.getContext('2d'); ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height); ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
    const data = canvas.toDataURL('image/jpeg', .86);
    if (data.length > 4200000) throw new Error('La imagen sigue siendo demasiado grande. Usa una captura mas pequena.');
    return { kind, image: data, text: '' };
  } finally { image.close(); }
}
