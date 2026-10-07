// Inicializa FullCalendar con los eventos de /api/eventos
document.addEventListener('DOMContentLoaded', function () {
    const contenedor = document.getElementById('calendario');
    const esCelular = window.innerWidth < 768;

    const calendario = new FullCalendar.Calendar(contenedor, {
        locale: 'es',
        firstDay: 1,
        initialView: esCelular ? 'listWeek' : 'timeGridWeek',
        headerToolbar: esCelular
            ? { left: 'prev,next', center: 'title', right: 'today' }
            : { left: 'prev,next today', center: 'title', right: 'dayGridMonth,timeGridWeek,listWeek' },
        footerToolbar: esCelular ? { center: 'dayGridMonth,timeGridWeek,listWeek' } : undefined,
        buttonText: { today: 'Hoy', month: 'Mes', week: 'Semana', list: 'Agenda' },
        allDayText: 'Entregas',
        noEventsText: 'No hay eventos en este periodo',
        height: 'auto',
        nowIndicator: true,
        slotMinTime: '07:00:00',
        slotMaxTime: '24:00:00',
        dayMaxEvents: 4,
        events: contenedor.dataset.urlEventos,
        eventDidMount: function (info) {
            info.el.title = info.event.title;
        }
    });

    calendario.render();
});
