# Instalar en Android y iPhone

La misma aplicacion se instala como PWA en ambos sistemas. No es una publicacion
en Play Store ni App Store. Las opciones exactas dependen del navegador y su version.

## Publicar los cambios

1. Ejecutar `mvn verify` y `node --test scripts/test-pwa.mjs`.
2. Subir los cambios de este proyecto a `main` en GitHub.
3. Esperar a que GitHub Actions termine y Render muestre el nuevo despliegue como **Live**.
4. Abrir https://planificador-academico.onrender.com desde el celular.

No hace falta cambiar Neon, las claves de IA ni las cuentas existentes.
No instalar desde `http://IP-del-PC`: la instalacion y el worker requieren HTTPS,
excepto `localhost` en la propia computadora.

## Android

1. Abrir el enlace en Chrome (no dentro de Instagram, WhatsApp u otro navegador integrado).
2. Usar **Instalar aplicacion**, cuando aparezca en el login o en el menu de la app.
   Tambien se puede usar el menu de Chrome > **Instalar aplicacion** o **Anadir a pantalla de inicio**.
3. Confirmar. En adelante se abre desde su icono.

## iPhone

1. Abrir el enlace en Safari.
2. Abrir el menu de compartir y elegir **Anadir a pantalla de inicio**.
3. Activar **Abrir como app web**, si esa opcion aparece, y confirmar **Anadir**.
4. Abrir desde el nuevo icono. Puede ser necesario iniciar sesion de nuevo.

Safari no ofrece el mismo evento de instalacion que Chrome; por eso no se muestra
un boton que no pueda instalarla. No se requiere una cuenta de desarrollador Apple
para instalarla de esta forma.

## Conexion, datos y actualizaciones

- Se necesita internet para iniciar sesion, consultar datos, guardar cambios o usar IA.
- Sin conexion aparece una pantalla publica al navegar. Los formularios no se guardan
  ni se envian en segundo plano; un envio sin conexion puede mostrar el error del navegador.
- El worker solo precarga esa pantalla y los iconos. Nunca guarda en Cache Storage
  paginas autenticadas, claves, respuestas de API, silabos ni datos personales.
- La instalacion no evita que Render Free suspenda el servidor cuando no hay actividad.
- Las paginas y las peticiones privadas siempre se consultan en la red. Las actualizaciones
  se reciben al volver a abrir; no hace falta reinstalar para cada cambio.
- Si se modifica la pantalla sin conexion o sus iconos, aumentar la version de `CACHE`
  en `src/main/resources/static/sw.js`.
- Los PNG se regeneran desde la raiz con `java scripts/GenerarIconos.java`.

## Comprobacion manual

- Android/Chrome: revisar Manifest y Service Workers en DevTools, instalar y abrir desde el icono.
- iPhone/Safari: anadir a inicio y abrir en una ventana propia; comprobar teclado y barras seguras.
- Probar login, crear cuenta, navegar entre las cinco secciones y cerrar sesion.
- Tras una visita conectada, cortar la red y abrir una ruta: debe mostrarse la pantalla publica,
  nunca una copia del plan del usuario anterior. Reconectar y pulsar **Volver a intentar**.

Referencias: [Google Chrome](https://support.google.com/chrome/answer/9658361?co=GENIE.Platform%3DAndroid&hl=es),
[Apple](https://support.apple.com/es-es/guide/iphone/iphea86e5236/ios).
