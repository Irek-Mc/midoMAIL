/**
 * Router po stronie klienta oparty na hashu (60-UX-Filozofia.md §3 - 10 obszarów), bez frameworka
 * (decyzja z Kontekstu planu Fazy 6). Każdy obszar rejestruje funkcję renderującą przez
 * Router.register(area, fn) - fn otrzymuje element #app-content do wypełnienia.
 */
const Router = (() => {
    const routes = {};

    function register(area, renderFn) {
        routes[area] = renderFn;
    }

    function currentArea() {
        const hash = window.location.hash.replace(/^#\//, '') || 'dashboard';
        return hash.split('/')[0];
    }

    async function navigate() {
        const area = currentArea();
        document.querySelectorAll('.app-nav a').forEach((link) => {
            link.classList.toggle('active', link.dataset.area === area);
        });
        const content = document.getElementById('app-content');
        const renderFn = routes[area];
        if (renderFn) {
            await renderFn(content);
        } else {
            content.innerHTML = `<p>Nieznany obszar: ${area}</p>`;
        }
    }

    window.addEventListener('hashchange', navigate);

    return { register, navigate, currentArea };
})();
