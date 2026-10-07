// Pantallas "Subir sílabo" y "Revisar sílabo"
document.addEventListener('DOMContentLoaded', function () {

    // ---------- Subida: nombre del archivo, arrastrar y pantalla de espera ----------
    const archivo = document.getElementById('archivo');
    const zona = document.getElementById('zonaSubida');
    if (archivo && zona) {
        const etiqueta = document.getElementById('nombreArchivo');
        const textoInicial = etiqueta.textContent;
        archivo.addEventListener('change', function () {
            const elegido = archivo.files[0];
            etiqueta.textContent = elegido ? elegido.name : textoInicial;
            zona.classList.toggle('con-archivo', !!elegido);
        });
        ['dragenter', 'dragover'].forEach(evento => zona.addEventListener(evento, e => {
            e.preventDefault();
            zona.classList.add('arrastrando');
        }));
        ['dragleave', 'drop'].forEach(evento => zona.addEventListener(evento, () => zona.classList.remove('arrastrando')));
        zona.addEventListener('drop', e => {
            e.preventDefault();
            if (e.dataTransfer.files.length) {
                archivo.files = e.dataTransfer.files;
                archivo.dispatchEvent(new Event('change'));
            }
        });
        document.getElementById('formSubida').addEventListener('submit', function () {
            if (archivo.files.length) {
                document.getElementById('cargando').hidden = false;
            }
        });
    }

    // ---------- Revisión: agregar / quitar filas y total de pesos ----------
    document.querySelectorAll('[data-agregar]').forEach(boton => boton.addEventListener('click', function () {
        const filas = document.getElementById(boton.dataset.agregar);
        const plantilla = document.getElementById(boton.dataset.plantilla);
        filas.insertAdjacentHTML('beforeend', plantilla.innerHTML.replaceAll('__i__', filas.children.length));
        const nueva = filas.lastElementChild.querySelector('input');
        if (nueva) nueva.focus();
        actualizarTotal();
    }));

    document.addEventListener('click', function (e) {
        const boton = e.target.closest('[data-quitar]');
        if (!boton) return;
        const fila = boton.closest('tr');
        const filas = fila.parentElement;
        fila.remove();
        reindexar(filas);
        actualizarTotal();
    });

    document.addEventListener('input', function (e) {
        if (e.target.matches('[data-peso]')) actualizarTotal();
    });

    // Spring necesita índices consecutivos: evaluaciones[0], evaluaciones[1], ...
    function reindexar(filas) {
        Array.from(filas.children).forEach((fila, indice) => {
            fila.querySelectorAll('[name]').forEach(campo => {
                campo.name = campo.name.replace(/^(\w+)\[\d+\]/, '$1[' + indice + ']');
                campo.removeAttribute('id');
            });
        });
    }

    function actualizarTotal() {
        const total = document.getElementById('totalPeso');
        if (!total) return;
        let suma = 0;
        document.querySelectorAll('#filasEvaluaciones [data-peso]').forEach(c => suma += parseFloat(c.value) || 0);
        total.querySelector('strong').textContent = suma.toFixed(1) + '%';
        total.classList.toggle('ok', Math.abs(suma - 100) < 0.05);
    }
});
