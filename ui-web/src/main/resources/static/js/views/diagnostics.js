/**
 * Ekran Diagnostyka (Iteracja 6.20, 67-Diagnostyka.md). Reużywa `GET /messages/find`
 * (Iteracja 6.10) do wyszukania komunikatu po ExternalReference/MessageId, następnie
 * `GET /diagnostics/trace?correlationId=` (Faza 5) do pełnego śladu.
 *
 * „Obszary diagnostyczne" (§2: Gateway Engine/Routing/Exactly Once/Scheduler/Event Bus/Baza
 * danych/Platforma — stan komponentów) świadomie niedostępne — ta sama kategoria luki co
 * „Kolejki" w Dashboardzie (ADR-0034): Core nie udostępnia generycznej introspekcji tych
 * komponentów wewnętrznych poza tym, co już zbudowano (zdrowie adapterów, ślad komunikatu).
 * Rozstrzygnięte przez analogię do już potwierdzonej decyzji użytkownika dla Dashboardu, nie
 * przez nowe pytanie — odnotowane wprost do przeglądu.
 */
Router.register('diagnostics', (content) => {
    content.innerHTML = `
        <h2>Diagnostyka</h2>

        <section class="dashboard-section">
            <h3>Obszary diagnostyczne</h3>
            <p class="unavailable">Stan poszczególnych komponentów (Gateway Engine/Routing/Exactly Once/Scheduler/Event Bus/Baza danych/Platforma) niedostępny w tej fazie — Core nie udostępnia dziś generycznej introspekcji tych komponentów. Zdrowie adapterów dostępne w <a href="#/adapters">Adaptery</a>.</p>
        </section>

        <section class="dashboard-section">
            <h3>Wyszukiwanie</h3>
            <form id="diagnostics-search-form">
                <label for="search-type">Szukaj po</label>
                <select id="search-type">
                    <option value="externalReference">ExternalReference</option>
                    <option value="messageId">MessageId</option>
                </select>
                <input type="text" id="search-value" required>
                <button type="submit">Szukaj</button>
            </form>
            <p id="diagnostics-error" class="error-message" hidden></p>
        </section>

        <section class="dashboard-section" id="diagnostics-result" hidden>
            <h3>Ślad komunikatu</h3>
            <div id="diagnostics-message"></div>
            <h4>Zdarzenia domenowe</h4>
            <ul id="diagnostics-events"></ul>
        </section>

        <section class="dashboard-section">
            <h3>Operacje</h3>
            <button id="diagnostics-export" disabled>Eksport raportu diagnostycznego</button>
            <a href="#/logs">Przejście do logów</a>
        </section>
    `;

    let lastReport = null;

    document.getElementById('diagnostics-search-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const searchType = document.getElementById('search-type').value;
        const searchValue = document.getElementById('search-value').value;
        const errorMessage = document.getElementById('diagnostics-error');
        const resultSection = document.getElementById('diagnostics-result');
        errorMessage.hidden = true;
        resultSection.hidden = true;

        const findResponse = await Api.get(`/messages/find?${searchType}=${encodeURIComponent(searchValue)}`);
        if (findResponse.status === 404) {
            errorMessage.textContent = 'Nie znaleziono komunikatu';
            errorMessage.hidden = false;
            return;
        }
        const message = await findResponse.json();

        const traceResponse = await Api.get(`/diagnostics/trace?correlationId=${encodeURIComponent(message.correlationId)}`);
        const trace = await traceResponse.json();

        document.getElementById('diagnostics-message').innerHTML = `
            <p>MessageId: ${message.messageId} · CorrelationId: ${message.correlationId} · ExternalReference: ${message.externalReference}</p>
            <p>Stan przetwarzania: ${message.processingState} · ${message.sourceChannel} → ${message.destinationChannel}</p>
        `;
        document.getElementById('diagnostics-events').innerHTML = trace.events.map((event) =>
            `<li>${event.timestamp} — ${event.eventType} (${event.category})</li>`
        ).join('') || '<li>Brak zdarzeń</li>';

        lastReport = { message, trace };
        resultSection.hidden = false;
        const exportButton = document.getElementById('diagnostics-export');
        exportButton.disabled = false;
        exportButton.onclick = () => {
            const blob = new Blob([JSON.stringify(lastReport, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `midomail-diagnostics-${message.messageId}.json`;
            link.click();
            URL.revokeObjectURL(url);
        };
    });
});
