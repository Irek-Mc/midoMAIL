/**
 * Ekran Konfiguracja (Iteracja 6.26, 65-Konfiguracja.md). Reużywa w pełni istniejące endpointy
 * REST (`GET /config/yaml/export`, `POST /config/yaml/validate|import`, `GET /config/yaml/history`,
 * `POST /config/yaml/rollback` — Iteracja 6.14). Zero nowych endpointów.
 *
 * „Zarządzanie sekretami" (§4: „hasła i klucze nigdy w jawnej postaci") — schemat YAML
 * (SPEC-0005) niesie wyłącznie `credentials.secretRef` (referencję do Secret Store), nigdy
 * surowe hasło — ten ekran nie renderuje pola hasła wprost z tego powodu (nie ma go w
 * dokumencie YAML). Typowana konfiguracja adaptera z polami hasła — ekran Adaptery
 * (Iteracja 6.25).
 *
 * „Podgląd zmian" — porównanie aktualnie wyeksportowanego YAML z edytowanym tekstem,
 * wyłącznie prezentacyjne (bez lokalnej walidacji biznesowej — walidacja to zawsze
 * `POST /config/yaml/validate`).
 */
Router.register('configuration', async (content) => {
    content.innerHTML = `
        <h2>Konfiguracja</h2>

        <section class="dashboard-section">
            <textarea id="config-yaml-editor" rows="20" style="width:100%; font-family: monospace;"></textarea>
            <div>
                <button id="config-load">Wczytaj bieżącą (Eksport)</button>
                <button id="config-validate">Walidacja</button>
                <button id="config-preview">Podgląd zmian</button>
                <button id="config-import">Zapisz (Import)</button>
                <button id="config-rollback">Przywróć poprzednią wersję</button>
            </div>
            <p id="config-result"></p>
        </section>

        <section class="dashboard-section">
            <h3>Podgląd zmian</h3>
            <pre id="config-preview-result"></pre>
        </section>

        <section class="dashboard-section">
            <h3>Historia zmian</h3>
            <ul id="config-history-list"></ul>
        </section>
    `;

    const editor = document.getElementById('config-yaml-editor');
    const result = document.getElementById('config-result');
    let lastExported = '';

    async function loadCurrent() {
        const response = await Api.get('/config/yaml/export');
        if (response.status === 404) {
            result.textContent = 'Brak zapisanej konfiguracji';
            return;
        }
        lastExported = await response.text();
        editor.value = lastExported;
        result.textContent = '';
    }

    async function loadHistory() {
        const response = await Api.get('/config/yaml/history');
        const history = await response.json();
        document.getElementById('config-history-list').innerHTML = history.map((yamlText, index) =>
            `<li>Wersja ${index + 1} (${yamlText.length} znaków) <button onclick="window.__midomailShowHistoryVersion(${index})">Podgląd</button></li>`
        ).join('') || '<li>Brak historii</li>';
        window.__midomailConfigHistory = history;
    }

    window.__midomailShowHistoryVersion = (index) => {
        document.getElementById('config-preview-result').textContent = window.__midomailConfigHistory[index];
    };

    document.getElementById('config-load').addEventListener('click', loadCurrent);

    document.getElementById('config-validate').addEventListener('click', async () => {
        const response = await Api.postRaw('/config/yaml/validate', editor.value);
        if (!response.ok) {
            result.textContent = `Nieprawidłowy YAML: ${await response.text()}`;
            return;
        }
        const validation = await response.json();
        result.textContent = validation.valid
            ? 'Konfiguracja poprawna'
            : `Błędy walidacji: ${validation.errors.map((e) => `${e.field}: ${e.message}`).join('; ')}`;
    });

    document.getElementById('config-preview').addEventListener('click', () => {
        document.getElementById('config-preview-result').textContent =
            `--- Bieżąca ---\n${lastExported}\n\n--- Edytowana ---\n${editor.value}`;
    });

    document.getElementById('config-import').addEventListener('click', async () => {
        const response = await Api.postRaw('/config/yaml/import', editor.value);
        if (response.status === 422) {
            const validation = await response.json();
            result.textContent = `Odrzucone: ${validation.errors.map((e) => `${e.field}: ${e.message}`).join('; ')}`;
            return;
        }
        if (!response.ok) {
            result.textContent = `Błąd: ${await response.text()}`;
            return;
        }
        result.textContent = 'Zapisano';
        lastExported = editor.value;
        await loadHistory();
    });

    document.getElementById('config-rollback').addEventListener('click', async () => {
        const response = await Api.post('/config/yaml/rollback');
        if (response.status === 409) {
            result.textContent = 'Brak historii do przywrócenia';
            return;
        }
        result.textContent = 'Przywrócono poprzednią wersję';
        await loadCurrent();
        await loadHistory();
    });

    await loadCurrent();
    await loadHistory();
});
