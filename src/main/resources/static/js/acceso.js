document.querySelectorAll('.ver-password').forEach(function (boton) {
    boton.addEventListener('click', function () {
        const campo = boton.parentElement.querySelector('input');
        const visible = campo.type === 'password';
        campo.type = visible ? 'text' : 'password';
        boton.setAttribute('aria-pressed', String(visible));
        boton.title = visible ? 'Ocultar contraseña' : 'Mostrar contraseña';
        boton.setAttribute('aria-label', boton.title);
        boton.querySelector('i').className = visible ? 'bi bi-eye-slash' : 'bi bi-eye';
    });
});
