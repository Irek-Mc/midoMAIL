/**
 * Szkielet aplikacji (Iteracja 6.17) - logowanie, powłoka, dyspozycja do widoków. Wszystkie 10
 * obszarów (60-UX-Filozofia.md §3) mają rzeczywiste widoki (js/views/*.js, Iteracje 6.18-6.27,
 * wczytane PRZED tym plikiem w index.html) - PLACEHOLDER_AREAS pozostaje pustą tablicą jako
 * mechanizm awaryjny na wypadek przyszłego nowego obszaru bez widoku.
 */

function showLogin() {
    document.getElementById('login-screen').hidden = false;
    document.getElementById('app-shell').hidden = true;
}

function showApp() {
    document.getElementById('login-screen').hidden = true;
    document.getElementById('app-shell').hidden = false;
    Router.navigate();
}

document.getElementById('login-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const apiKey = document.getElementById('api-key').value;
    Api.setApiKey(apiKey);
    const errorMessage = document.getElementById('login-error');
    try {
        await Api.get('/adapters');
        errorMessage.hidden = true;
        showApp();
    } catch (error) {
        Api.clearApiKey();
        errorMessage.textContent = 'Nieprawidłowy klucz API';
        errorMessage.hidden = false;
    }
});

document.getElementById('logout-button').addEventListener('click', () => {
    Api.clearApiKey();
    showLogin();
});

window.addEventListener('midomail:unauthorized', showLogin);

const PLACEHOLDER_AREAS = [];
PLACEHOLDER_AREAS.forEach((area) => {
    Router.register(area, (content) => {
        content.innerHTML = `<h2>${area}</h2><p>Ekran w budowie — kolejna iteracja Fazy 6.</p>`;
    });
});

if (Api.isAuthenticated()) {
    showApp();
} else {
    showLogin();
}
