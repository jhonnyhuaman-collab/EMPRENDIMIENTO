# Verificacion local

Fecha: 4 de noviembre de 2026.

- 28 pruebas automatizadas aprobadas con `npm test`: autenticacion, privacidad
  entre cuentas, CSRF, limites de intentos, concurrencia, validaciones, planificador,
  semanas A/B, calendario ICS, conversion desde Java y respuestas simuladas de IA.
- `npm run build`: compilacion de produccion correcta. El empaquetador informa
  advertencias de directivas `use client` de dependencias; no impiden la compilacion.
- `npm run check:functions`: funcion API empaquetada correctamente para Netlify.
- Chrome automatizado: flujos completos en 1440x1000, 412x915, 390x844 y
  360x800. Registro, cursos, evaluaciones, dias de estudio, clases, notas,
  calendario, cierre de sesion y pantalla sin conexion correctos, sin desborde
  horizontal ni errores JavaScript. No se encontraron paginas privadas en cache.
- Extraccion real de un PDF sintetico en el navegador, revision editable y
  guardado comprobados. La respuesta de IA se simulo; el guardado uso el API real.
- El manifiesto paso la comprobacion de instalabilidad de Chrome. Los tamanos
  Android/iPhone son emulaciones de pantalla, no pruebas en dispositivos reales.
- PDF.js utiliza su distribucion compatible con navegadores anteriores; libera
  el documento mediante su tarea de carga.

Estas verificaciones usan PostgreSQL local de PGlite y cuentas de prueba. No
demuestran una conexion real a Neon ni un despliegue en Netlify. La IA se simula
para no consumir claves ni cuotas. Tampoco se ha ejecutado la migracion sobre
las bases reales de Java.

Antes de reemplazar Render, verificar en el sitio publicado: registro, acceso,
sesion tras recargar, importacion real con el proveedor configurado, descarga ICS
e instalacion desde un Android y un iPhone reales.
