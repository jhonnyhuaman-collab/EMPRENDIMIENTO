# Porcentajes y distribucion equivalentes a Render

Verificacion del 5 de noviembre de 2026.

Se compilaron y ejecutaron los servicios Java originales del proyecto con JDK 21:
PlanificadorService, PrioridadService, VentanaPreparacionService,
DisponibilidadService, PlanRepasoService y CalendarioAcademicoService.
Los repositorios se sustituyeron por datos sinteticos en memoria. No se inicio
Spring Boot ni se consultaron bases de datos locales o de produccion.

Se compararon las fechas, horas de inicio/fin, evaluacion, descripcion del tema
y estado completado de cada bloque en 32 escenarios. Los resultados de Netlify
coincidieron con los del Java original en los 32 casos.
Los resultados de referencia estan en `tests/fixtures/render-planner.json` y se
comprueban con `npm test`, sin necesitar Java en Netlify ni en GitHub Actions.
Esto verifica esos escenarios, no todos los posibles datos de cada usuario.

Correcciones trasladadas del algoritmo original:

- Empates de prioridad resueltos conservando el orden por fecha.
- Segunda pasada de rescate solo para cursos que aun no recibieron tiempo.
- Una sesion nueva de al menos una hora, excepto rescate o ultimo saldo.
- Preferir una franja de al menos una hora despues de la ultima clase.
- Recalcular disponibilidad tras colocar cada bloque y dejar 15 min de descanso.
- Consumir los pasos de repaso en orden, respetando las horas ya completadas.
- Conservar limites diarios, semanales por curso, clases A/B y hora de dormir.

Porcentajes: los campos usan `step="any"` dentro del rango 0..100. No se convierten
fracciones automaticamente: si introduces 0.25, se guarda 0.25%, no 25%.
Se elimina el redondeo visual a una decima. Se verifico en Chrome el ingreso de
0.25, 0.001 y 19.875, y el guardado y visualizacion de 0.25%.

Validacion final: 82 pruebas aprobadas, compilacion de produccion y funcion
empaquetada para Netlify. Tras publicar, pulsar Actualizar mi plan para regenerar
los bloques pendientes. Los completados se conservan.
