/**
 * Ekran Logi (Iteracja 6.21, 69-Logi.md). `GET /logs` (Iteracja 6.21) — logi zapisane przez
 * `EventStoreSystemLogger` (Faza 4) jako `Event(category=DIAGNOSTIC)`.
 *
 * SA-11 (strumień na żywo): odświeżanie przez cykliczne odpytywanie co 3 s, nie SSE/WebSocket —
 * `com.sun.net.httpserver.HttpServer` bez natywnego wsparcia, świadome uproszczenie (SPEC-0025).
 *
 * Filtrowanie po MessageId/ExternalReference niedostępne (Event niesie wyłącznie CorrelationId) —
 * „Kopiowanie CorrelationId" w Diagnostyce (Iteracja 6.20) prowadzi tu przez pole filtra.
 */
Router.register('logs', (content) => {
    content.innerHTML = `
        <h2>Logi</h2>

        <section class="dashboard-section">
            <form id="logs-filter-form">
                <label for="filter-level">Poziom</label>
                <select id="filter-level">
                    <option value="">(wszystkie)</option>
                    <option value="INFO">INFO</option>
                    <option value="WARN">WARN</option>
                    <option value="ERROR">ERROR</option>
                </select>
                <label for="filter-component">Komponent</label>
                <input type="text" id="filter-component">
                <label for="filter-correlation-id">CorrelationId</label>
                <input type="text" id="filter-correlation-id">
                <button type="submit">Filtruj</button>
                <label><input type="checkbox" id="live-toggle"> Strumień na żywo (odpytywanie co 3 s)</label>
            </form>
        </section>

        <section class="dashboard-section">
            <table>
                <thead><tr><th>Czas</th><th>Poziom</th><th>Komponent</th><th>CorrelationId</th><th>Treść</th></tr></thead>
                <tbody id="logs-body"></tbody>
            </table>
        </section>
    `;

    let liveIntervalId = null;

    async function loadLogs() {
        const level = document.getElementById('filter-level').value;
        const component = document.getElementById('filter-component').value;
        const correlationId = document.getElementById('filter-correlation-id').value;
        const params = new URLSearchParams();
        if (level) params.set('level', level);
        if (component) params.set('component', component);
        if (correlationId) params.set('correlationId', correlationId);

        const response = await Api.get(`/logs?${params.toString()}`);
        const entries = await response.json();

        document.getElementById('logs-body').innerHTML = entries.map((entry) => `
            <tr>
                <td>${entry.timestamp}</td>
                <td>${entry.level}</td>
                <td>${entry.sourceComponent}</td>
                <td>
                    <button title="Kopiuj CorrelationId" onclick="navigator.clipboard.writeText('${entry.correlationId}')">${entry.correlationId}</button>
                </td>
                <td>${entry.message}</td>
            </tr>
        `).join('');
    }

    document.getElementById('logs-filter-form').addEventListener('submit', (event) => {
        event.preventDefault();
        loadLogs();
    });

    document.getElementById('live-toggle').addEventListener('change', (event) => {
        if (event.target.checked) {
            liveIntervalId = window.setInterval(loadLogs, 3000);
        } else if (liveIntervalId !== null) {
            window.clearInterval(liveIntervalId);
            liveIntervalId = null;
        }
    });

    loadLogs();
});
