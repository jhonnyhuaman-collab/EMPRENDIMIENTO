# Mejoras de lectura de documentos

- GPT-OSS 120B/20B en Groq reciben un esquema JSON estricto con todos los campos
  requeridos y objetos cerrados. Los modelos personalizados conservan JSON mode.
- La respuesta identifica el tipo de documento. Un horario recibido en la opcion
  de silabos muestra instrucciones para abrir Mi horario, sin inventar un curso.
- Se reconocen aliases conocidos en espanol para datos del curso.
- Se rechazan respuestas truncadas aunque contengan una parte parseable.
- En Gemini se separa el texto final de las partes marcadas como razonamiento.
- Las tablas PDF se reconstruyen por coordenadas para mantener filas y columnas.
- Se mantienen la revision manual, la validacion final y el guardado explicito.

Verificado con 45 pruebas locales, compilacion y empaquetado de la funcion.
No se hicieron llamadas reales a proveedores ni se uso el PDF privado del usuario.
El esquema asegura la estructura de la respuesta; no garantiza que la IA interprete
correctamente el contenido. Siempre revisar fechas, clases, pesos y temas.

Referencia: [Groq Structured Outputs](https://console.groq.com/docs/structured-outputs).
