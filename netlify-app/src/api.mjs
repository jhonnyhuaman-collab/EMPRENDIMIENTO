export async function request(path, { method = 'GET', data, csrfToken, signal } = {}) {
  const response = await fetch('/api' + path, {
    method, credentials: 'same-origin', signal,
    headers: { ...(data !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(csrfToken ? { 'X-CSRF-Token': csrfToken } : {}) },
    ...(data !== undefined ? { body: JSON.stringify(data) } : {})
  });
  const body = await response.json().catch(() => ({ error: 'El servidor no devolvio una respuesta valida.' }));
  if (!response.ok) throw Object.assign(new Error(body.error || 'No se pudo completar la solicitud.'), { status: response.status });
  return body;
}
export async function download(path, filename) {
  const response = await fetch('/api' + path, { credentials: 'same-origin' });
  if (!response.ok) throw new Error('No se pudo descargar el archivo. Revisa tu sesion.');
  const url = URL.createObjectURL(await response.blob());
  const a = document.createElement('a'); a.href = url; a.download = filename; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
