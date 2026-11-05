# Preparacion por temas durante el ciclo de 16 semanas

El plan actual conserva las restricciones de Render (clases, descansos, dias de
estudio, hora de dormir, limites diarios y semanales, y bloques completados), y
amplia la preparacion de examenes y proyectos al periodo del ciclo que tienen
disponible. Para un final posterior al parcial, la nueva etapa comienza despues
del parcial. Practicas, laboratorios y tareas mantienen ventanas mas cortas.

## Temas y semanas

- Ordena naturalmente los numeros: 1.2 va antes de 1.10.
- Resuelve referencias como "6.1" al titulo completo del curso cuando existe.
- Usa los temas asociados a la evaluacion. Si faltan, utiliza los del curso con
  una advertencia para que el estudiante revise la asociacion.
- `topicSchedule` guarda la semana de cada tema solo cuando aparece en el
  silabo o la configura el usuario. Puede editarse al revisar el silabo o en
  Mis cursos, Editar curso, Semana de cada tema.
- Si no hay semanas conocidas, propone una distribucion por orden y lo indica
  como propuesta. No se presenta como calendario extraido del documento.
- Si no hay titulos de temas, no los inventa: genera pasos generales y avisa.

## Horas, repaso y calendario

Los pasos suman las horas estimadas, sin imponer media hora adicional por cada
tema. Cuando hay demasiados temas para ese presupuesto, varios temas se agrupan
en una sesion. No se descartan los ultimos temas para hacerlos encajar.

La preparacion se reparte progresivamente entre los dias disponibles. Los examenes
y practicas reservan aproximadamente 20% del tiempo para repaso final y simulacro
en la ultima semana antes de la evaluacion, si hay al menos una hora de presupuesto.
El estudio termina antes del examen y no se extiende fuera de las 16 semanas.

El calendario muestra Tu preparacion en 16 semanas con temas, fechas, horas y
evaluaciones. No se rellenan semanas pasadas: quien comienza a mitad del ciclo
recibe un plan para las semanas restantes. Con pocas horas o restricciones fuertes
puede haber semanas sin sesiones; no se agregan horas ficticias para rellenarlas.
Si no alcanza el tiempo o un tema se libera demasiado tarde, se conserva el aviso.

## Activar sobre datos existentes

Tras publicar, pulsar Actualizar mi plan. Revisar que las evaluaciones tengan sus
temas y que el lunes de la semana 1 sea correcto en Configuracion. Los bloques
completados se conservan; no es necesario borrar cursos ni volver a subir PDFs.

La funcion `generateRenderPlan` conserva la referencia del algoritmo Java y sus
32 escenarios de comparacion. `generatePlan` usa ahora la ampliacion semestral:
es una diferencia intencional solicitada para preparar el ciclo, no una afirmacion
de que el calendario completo sea identico al de la version Java.
