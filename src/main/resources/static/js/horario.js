// "Mi horario": grilla semanal de clases, un color por curso
document.addEventListener('DOMContentLoaded', function () {
    const contenedor = document.getElementById('horarioSemanal');
    if (!contenedor) return;
    const esCelular = window.innerWidth < 768;
    const formatoHora = { hour: '2-digit', minute: '2-digit', hour12: false };

    function escapar(texto) {
        const div = document.createElement('div');
        div.textContent = texto || '';
        return div.innerHTML;
    }

    const calendario = new FullCalendar.Calendar(contenedor, {
        locale: 'es',
        firstDay: 1,
        initialView: esCelular ? 'timeGridDay' : 'timeGridWeek',
        headerToolbar: esCelular ? { left: 'prev', center: 'title', right: 'next' } : false,
        titleFormat: { weekday: 'long' },
        dayHeaderFormat: { weekday: esCelular ? 'long' : 'long' },
        hiddenDays: [0],          // domingo
        allDaySlot: false,
        slotMinTime: '07:00:00',
        slotMaxTime: '23:00:00',
        slotLabelFormat: formatoHora,
        eventTimeFormat: formatoHora,
        height: 'auto',
        expandRows: true,
        nowIndicator: false,
        events: function (info, exito, error) {
            const semana = contenedor.dataset.semana || '';
            fetch(contenedor.dataset.urlEventos + (semana ? '?semana=' + semana : ''))
                .then(r => r.json()).then(exito).catch(error);
        },
        eventContent: function (info) {
            const detalle = info.event.extendedProps.detalle;
            return {
                html: '<div class="clase">'
                    + '<div class="clase-hora">' + escapar(info.timeText) + '</div>'
                    + '<div class="clase-titulo">' + escapar(info.event.title) + '</div>'
                    + (detalle ? '<div class="clase-detalle">' + escapar(detalle) + '</div>' : '')
                    + '</div>'
            };
        },
        eventDidMount: function (info) {
            const detalle = info.event.extendedProps.detalle;
            info.el.title = info.event.title + (detalle ? ' · ' + detalle : '');
        }
    });
    calendario.render();

    // Semana A / Semana B
    document.querySelectorAll('.opcion-semana').forEach(function (boton) {
        boton.addEventListener('click', function () {
            document.querySelectorAll('.opcion-semana').forEach(b => b.classList.remove('activa'));
            boton.classList.add('activa');
            contenedor.dataset.semana = boton.dataset.semana;
            calendario.refetchEvents();
        });
    });
});
