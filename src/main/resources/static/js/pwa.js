(() => {
    'use strict';

    const workerUrl = new URL('../sw.js', document.currentScript.src);
    const buttons = document.querySelectorAll('.pwa-instalar');
    const displayMode = window.matchMedia('(display-mode: standalone)');
    let installPrompt = null;

    const installed = () => displayMode.matches || navigator.standalone === true;
    const refreshButtons = () => buttons.forEach(button => {
        button.hidden = !installPrompt || installed();
    });

    window.addEventListener('beforeinstallprompt', event => {
        if (installed()) return;
        event.preventDefault();
        installPrompt = event;
        refreshButtons();
    });

    window.addEventListener('appinstalled', () => {
        installPrompt = null;
        refreshButtons();
    });
    displayMode.addEventListener('change', refreshButtons);

    buttons.forEach(button => button.addEventListener('click', async () => {
        if (!installPrompt) return;
        const prompt = installPrompt;
        installPrompt = null;
        refreshButtons();
        try {
            await prompt.prompt();
        } catch (error) {
            console.warn('No se pudo abrir la instalacion.', error);
        }
    }));

    if ('serviceWorker' in navigator && window.isSecureContext) {
        window.addEventListener('load', () => {
            navigator.serviceWorker.register(workerUrl.href, { updateViaCache: 'none' })
                .catch(error => console.warn('No se pudo preparar el modo sin conexion.', error));
        });
    }
})();
