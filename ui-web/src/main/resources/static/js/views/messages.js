/**
 * Ekran Komunikaty (Iteracja 6.23, 62-Komunikaty.md). Reużywa `GET /messages`, `GET /messages/find`,
 * `GET /diagnostics/trace`, `POST /messages/reprocess` (Iteracja 6.10/Faza 5). Filtry odpowiadają
 * wprost `MessageQueryFilter` (§3) — UI nie implementuje własnej logiki wyszukiwania.
 *
 * „Ponowne przetworzenie" (§5) — jawnie potwierdzana operacja (dialog `confirm()`
 * z ostrzeżeniem „unieważnia deduplikację"), zgodnie z ADR-0028: świadomie unieważnia rekord
 * deduplikacji, nie jest cichym automatycznym ponowieniem.
 */
Router.register('messages', async (content) => {
    content.innerHTML = `
        <h2>Komunikaty</h2>

        <section class="dashboard-section">
            <form id="messages-filter-form">
                <label for="filter-channel">Kanał</label>
                <input type="text" id="filter-channel">
                <label for="filter-adapter">Adapter</label>
                <input type="text" id="filter-adapter">
                <label for="filter-state">Status przetwarzania</label>
                <input type="text" id="filter-state">
                <label for="filter-priority">MessagePriority</label>
                <input type="text" id="filter-priority">
                <label for="filter-content">Pełnotekstowe wyszukiwanie</label>
                <input type="text" id="filter-content">
                <button type="submit">Szukaj</button>
            </form>
        </section>

        <section class="dashboard-section">
            <table>
                <thead>
                    <tr>
                        <th>MessageId</th><th>ExternalReference</th><th>Kanał źr.</th><th>Kanał doc.</th>
                        <th>Status</th><th>Priorytet</th><th>Utworzono</th><th>Zaktualizowano</th><th></th>
                    </tr>
                </thead>
                <tbody id="messages-body"></tbody>
            </table>
        </section>

        <section class="dashboard-section" id="messages-detail" hidden></section>
    `;

    async function loadMessages() {
        const params = new URLSearchParams();
        const channel = document.getElementById('filter-channel').value;
        const adapter = document.getElementById('filter-adapter').value;
        const state = document.getElementById('filter-state').value;
        const priority = document.getElementById('filter-priority').value;
        const contentSearch = document.getElementById('filter-content').value;
        if (channel) params.set('channelType', channel);
        if (adapter) params.set('adapterId', adapter);
        if (state) params.set('processingState', state);
        if (priority) params.set('messagePriority', priority);
        if (contentSearch) params.set('contentSearch', contentSearch);

        const response = await Api.get(`/messages?${params.toString()}`);
        const page = await response.json();

        document.getElementById('messages-body').innerHTML = page.items.map((message) => `
            <tr>
                <td>${message.messageId}</td>
                <td>${message.externalReference}</td>
                <td>${message.sourceChannel}</td>
                <td>${message.destinationChannel}</td>
                <td>${message.processingState}</td>
                <td>${message.messagePriority}</td>
                <td>${message.createdAt ?? '-'}</td>
                <td>${message.updatedAt ?? '-'}</td>
                <td>
                    <button onclick="window.__midomailShowMessageDetail('${message.messageId}')">Ślad</button>
                    <button onclick="window.__midomailReprocessMessage('${message.externalReference}')">Ponów</button>
                </td>
            </tr>
        `).join('') || '<tr><td colspan="9">Brak komunikatów</td></tr>';
    }

    async function showDetail(messageId) {
        const findResponse = await Api.get(`/messages/find?messageId=${encodeURIComponent(messageId)}`);
        const message = await findResponse.json();
        const traceResponse = await Api.get(`/diagnostics/trace?correlationId=${encodeURIComponent(message.correlationId)}`);
        const trace = await traceResponse.json();

        const detail = document.getElementById('messages-detail');
        detail.hidden = false;
        detail.innerHTML = `
            <h3>Szczegóły komunikatu ${messageId}</h3>
            <p>CorrelationId: ${message.correlationId} · CausationId: ${message.causationId ?? '-'}</p>
            <p>Treść: ${message.content}</p>
            <h4>Historia zdarzeń</h4>
            <ul>${trace.events.map((event) => `<li>${event.timestamp} — ${event.eventType}</li>`).join('') || '<li>Brak zdarzeń</li>'}</ul>
        `;
    }
    window.__midomailShowMessageDetail = showDetail;

    async function reprocess(externalReference) {
        const confirmed = window.confirm(
            'Ponowne przetworzenie świadomie unieważnia rekord deduplikacji Exactly Once dla tego ExternalReference. Kontynuować?'
        );
        if (!confirmed) return;
        await Api.post(`/messages/reprocess?externalReference=${encodeURIComponent(externalReference)}`);
        await loadMessages();
    }
    window.__midomailReprocessMessage = reprocess;

    document.getElementById('messages-filter-form').addEventListener('submit', (event) => {
        event.preventDefault();
        loadMessages();
    });

    await loadMessages();
});
