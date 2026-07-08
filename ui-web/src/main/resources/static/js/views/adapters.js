/**
 * Ekran Adaptery (Iteracja 6.25, 64-Adaptery.md). Reużywa w pełni istniejące endpointy REST
 * (`GET /adapters`, `POST /adapters/enable|disable|restart|test`, `DELETE /adapters`, `POST /adapters`
 * (dodaj, 501) — Faza 5; `GET/POST /adapters/configuration` — Iteracja 6.13).
 *
 * „Ostatnia aktywność" (§2, widok listy) świadomie niedostępna — ta sama kategoria ograniczenia
 * co „Kolejki"/„Obszary diagnostyczne": Core nie śledzi znacznika czasu ostatniej aktywności per
 * adapter (`Metrics` ma wyłącznie liczniki skumulowane).
 *
 * „Dodaj adapter" jawnie niedostępne (Faza 5, SPEC-0024 §Otwarte decyzje poz. 9) — przycisk
 * `disabled` z czytelnym `title`, nie ukrywa operacji.
 */
Router.register('adapters', async (content) => {
    content.innerHTML = `
        <h2>Adaptery</h2>

        <section class="dashboard-section">
            <button disabled title="Niedostępne w tej fazie — SPEC-0024 §Otwarte decyzje poz. 9, wymaga zmiany konfiguracji punktu kompozycji i restartu procesu">Dodaj adapter</button>
        </section>

        <section class="dashboard-section">
            <table>
                <thead>
                    <tr><th>Nazwa</th><th>Wersja</th><th>Stan</th><th>Kanały</th><th>Zdrowie</th><th></th></tr>
                </thead>
                <tbody id="adapters-body"></tbody>
            </table>
            <p class="unavailable">„Ostatnia aktywność" niedostępna — Core nie śledzi znacznika czasu per adapter.</p>
        </section>

        <section class="dashboard-section" id="adapter-detail" hidden></section>
    `;

    async function loadAdapters() {
        const response = await Api.get('/adapters');
        const adapters = await response.json();
        document.getElementById('adapters-body').innerHTML = adapters.map((adapter) => `
            <tr>
                <td>${adapter.adapterId}</td>
                <td>${adapter.adapterVersion}</td>
                <td>${adapter.state}</td>
                <td>${adapter.channels.join(', ')}</td>
                <td>${adapter.healthy ? 'zdrowy' : 'niezdrowy'}</td>
                <td>
                    <button onclick="window.__midomailShowAdapterDetail('${adapter.adapterId}')">Szczegóły</button>
                    <button onclick="window.__midomailAdapterAction('${adapter.adapterId}', 'enable')">Włącz</button>
                    <button onclick="window.__midomailAdapterAction('${adapter.adapterId}', 'disable')">Wyłącz</button>
                    <button onclick="window.__midomailAdapterAction('${adapter.adapterId}', 'restart')">Restart</button>
                    <button onclick="window.__midomailAdapterAction('${adapter.adapterId}', 'test')">Test połączenia</button>
                    <button onclick="window.__midomailRemoveAdapter('${adapter.adapterId}')">Usuń</button>
                </td>
            </tr>
        `).join('') || '<tr><td colspan="6">Brak adapterów</td></tr>';
        return adapters;
    }

    window.__midomailAdapterAction = async (adapterId, action) => {
        const response = await Api.post(`/adapters/${action}?id=${encodeURIComponent(adapterId)}`);
        if (action === 'test') {
            const healthy = await response.text();
            window.alert(`Wynik testu połączenia: ${healthy}`);
        }
        await loadAdapters();
    };

    window.__midomailRemoveAdapter = async (adapterId) => {
        const confirmed = window.confirm(`Usunąć adapter ${adapterId}?`);
        if (!confirmed) return;
        await Api.del(`/adapters?id=${encodeURIComponent(adapterId)}`);
        await loadAdapters();
    };

    window.__midomailShowAdapterDetail = async (adapterId) => {
        const adapters = await loadAdapters();
        const adapter = adapters.find((a) => a.adapterId === adapterId);
        if (!adapter) return;

        const adapterType = adapter.channels[0] ?? '';
        const configResponse = await Api.get(`/adapters/configuration?id=${encodeURIComponent(adapterId)}&type=${encodeURIComponent(adapterType)}`);
        const config = await configResponse.json();

        const detail = document.getElementById('adapter-detail');
        detail.hidden = false;
        detail.innerHTML = `
            <h3>Szczegóły adaptera ${adapterId}</h3>
            <p>Obsługiwane możliwości: ${adapter.capabilities.join(', ') || '-'}</p>
            <p>Statystyki: wysłane ${adapter.messagesSent} · odebrane ${adapter.messagesReceived} · błędy ${adapter.errorCount}</p>

            <h4>Kreator konfiguracji (typ: ${adapterType || 'nieznany'})</h4>
            <form id="adapter-config-form">
                ${Object.entries(config.fields).map(([field, value]) => `
                    <label for="config-field-${field}">${field}</label>
                    <input type="${field.includes('password') || field.includes('secretRef') ? 'password' : 'text'}"
                           id="config-field-${field}" data-field="${field}" value="${value ?? ''}">
                `).join('')}
                <button type="submit">Zapisz konfigurację</button>
            </form>
        `;

        document.getElementById('adapter-config-form').addEventListener('submit', async (event) => {
            event.preventDefault();
            const inputs = detail.querySelectorAll('#adapter-config-form input');
            for (const input of inputs) {
                const field = input.dataset.field;
                if (input.value) {
                    await Api.postRaw(`/adapters/configuration?id=${encodeURIComponent(adapterId)}&type=${encodeURIComponent(adapterType)}&field=${encodeURIComponent(field)}`, input.value);
                }
            }
            window.alert('Konfiguracja zapisana');
        });
    };

    await loadAdapters();
});
