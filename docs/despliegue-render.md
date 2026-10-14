# Render Free + PostgreSQL de Neon

## Datos y costos

La nube empieza sin cuentas registradas ni cursos. La carpeta `data/` de tu PC
no se sube, no se borra y no se migra. La primera persona debe usar Crear cuenta.
El contenedor solo contiene el ejecutable, no la base H2 ni claves de API.

Selecciona Free tanto en Render como en Neon. No actives planes de pago.
Los planes gratuitos tienen cuotas; Render puede suspenderse por inactividad.
No uses Render Postgres Free para conservar datos durante todo el curso:
esa base caduca a los 30 dias. Neon se usa como PostgreSQL externo.

## Crear la base de datos

1. Crea un proyecto vacio de Neon en el plan Free, en la region AWS US East
   (N. Virginia), cerca del servidor de Render.
2. En Connect, elige la base `neondb` y anota el host, usuario y password.
   Usa el host con `-pooler` para la aplicacion.
3. No pegues estas credenciales en GitHub, archivos del proyecto o el chat.
   Configuralas como secretos de Render.

## Publicar en Render

1. Conecta tu GitHub a Render y permite acceso al repositorio EMPRENDIMIENTO.
2. Crea un Blueprint desde
   `https://github.com/jhonnyhuaman-collab/EMPRENDIMIENTO`, rama `main`.
3. Render leera `render.yaml`: Docker, region Virginia y plan Free.
   Revisa que no se este seleccionando un plan de pago antes de confirmar.
4. Completa las variables solicitadas:

| Variable | Valor |
| --- | --- |
| `JDBC_DATABASE_URL` | `jdbc:postgresql://HOST_DE_NEON/neondb` |
| `DATABASE_USERNAME` | Usuario de Neon |
| `DATABASE_PASSWORD` | Password de Neon |

La URL no debe contener usuario ni password. Tampoco necesitas agregar
`sslmode`: el perfil `prod` exige TLS con verificacion del certificado y host.
No pegues directamente una URL que empiece por `postgresql://`: Java requiere
el prefijo `jdbc:postgresql://`.

5. Espera al despliegue. El chequeo `/actuator/health` debe responder
   `{"status":"UP"}` sin pedir login. Tambien comprueba la base PostgreSQL.
6. Abre el enlace `https://...onrender.com` que Render asigne y crea tu cuenta.

Las actualizaciones de `main` se publican automaticamente cuando pasan los
checks de GitHub Actions. Reiniciar o desplegar el contenedor no elimina Neon.
Haz exportaciones de respaldo de la base antes de cambios importantes.

## Activar la IA sin contratar un plan de pago

La aplicacion puede arrancar sin IA; permite registrar cursos y agenda
manualmente. El perfil de produccion usa solo Groq, sin fallback a Claude.

1. Comprueba en Groq Console que tu cuenta dice Free, no Developer.
2. Agrega `GROQ_API_KEY` como secreto en Render. No subas la clave a GitHub.
3. Guarda las variables y vuelve a desplegar. No configures `ANTHROPIC_API_KEY`.

Usar una sola IA no garantiza gratuidad si su cuenta ya tiene facturacion
activada. Al alcanzar las cuotas Free, hay que esperar o registrar manualmente.

## Pruebas antes de publicar

```powershell
mvn verify
```

Las pruebas PostgreSQL se activan cuando defines `POSTGRES_TEST_URL`,
`POSTGRES_TEST_USER` y `POSTGRES_TEST_PASSWORD`, apuntando a una base de pruebas
separada. Nunca las ejecutes contra la base de usuarios de Neon.
GitHub Actions crea un PostgreSQL efimero y ejecuta esas pruebas automaticamente.

```powershell
$env:POSTGRES_TEST_URL = "jdbc:postgresql://localhost:5432/planificador_test"
$env:POSTGRES_TEST_USER = "postgres"
$env:POSTGRES_TEST_PASSWORD = "PASSWORD_DE_PRUEBAS"
mvn verify
```

Las pruebas desactivan TLS solo para ese PostgreSQL local de pruebas. El perfil
de produccion sigue exigiendo TLS. Para levantarlo localmente necesitas Docker;
para publicarlo no necesitas instalar Docker en tu PC: Render construye la imagen.

El comando habitual `mvn spring-boot:run` sigue usando tu H2 local mientras no
actives `SPRING_PROFILES_ACTIVE=prod`. No actives ese perfil sin las credenciales
de PostgreSQL: el arranque debe fallar en lugar de guardar datos en archivos.

## Referencias

- [Render Blueprints](https://render.com/docs/blueprint-spec)
- [Limites gratuitos de Render](https://render.com/docs/free)
- [Neon Free](https://neon.com/pricing)
- [TLS de PostgreSQL JDBC](https://jdbc.postgresql.org/documentation/ssl/)
