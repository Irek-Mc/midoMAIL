/**
 * Ekran Statystyki (Iteracja 6.22, 68-Statystyki.md). Reużywa `GET /statistics` (Faza 5, filtr
 * `?adapterId=`), `GET /dashboard/exactly-once` (Iteracja 6.18), `GET /monitoring/resources`
 * (Iteracja 6.11) — zero nowych endpointów.
 *
 * „Średni czas obsługi" (§3, Adaptery) świadomie niedostępny — `MetricsSnapshot` nie śledzi
 * czasu przetwarzania, wyłącznie liczniki (ta sama kategoria ograniczenia co „Kolejki"/„Obszary
 * diagnostyczne" — Core nie ma dziś tej zdolności).
 *
 * „Wybór zakresu czasu" realizowany przez filtrowanie już pobranej listy migawek po
 * `periodStart`/`periodEnd` (pola kontraktu) — nie nowe wyliczenie, zgodnie z SPEC-0025 §Co już
 * jest pokryte.
 */
Router.register('statistics', async (content) => {
    content.innerHTML = '<p>Ładowanie…</p>';

    const [statisticsResponse, exactlyOnceResponse, resourcesResponse] = await Promise.all([
        Api.get('/statistics'),
        Api.get('/dashboard/exactly-once'),
        Api.get('/monitoring/resources')
    ]);
    const allSnapshots = await statisticsResponse.json();
    const exactlyOnce = await exactlyOnceResponse.json();
    const resources = await resourcesResponse.json();

    content.innerHTML = `
        <h2>Statystyki</h2>

        <div class="dashboard-section">
            <label for="statistics-adapter-filter">Filtruj po adapterze</label>
            <select id="statistics-adapter-filter">
                <option value="">(wszystkie)</option>
                ${[...new Set(allSnapshots.map((s) => s.adapterId))].map((id) => `<option value="${id}">${id}</option>`).join('')}
            </select>
            <label for="statistics-from">Od</label>
            <input type="datetime-local" id="statistics-from">
            <label for="statistics-to">Do</label>
            <input type="datetime-local" id="statistics-to">
            <button id="statistics-filter-apply">Zastosuj</button>
        </div>

        <div id="statistics-results"></div>
    `;

    function render(snapshots) {
        const results = document.getElementById('statistics-results');
        const totalSent = snapshots.reduce((sum, s) => sum + s.messagesSent, 0);
        const totalReceived = snapshots.reduce((sum, s) => sum + s.messagesReceived, 0);
        const totalErrors = snapshots.reduce((sum, s) => sum + s.errorCount, 0);

        results.innerHTML = `
            <section class="dashboard-section">
                <h3>Wolumen komunikatów</h3>
                <p>Wysłane: ${totalSent} · Odebrane: ${totalReceived} · Błędy: ${totalErrors}</p>
            </section>

            <section class="dashboard-section">
                <h3>Exactly Once</h3>
                <p>Przetworzone: ${exactlyOnce.processed} · Zapobieżone duplikaty: ${exactlyOnce.duplicatesPrevented}</p>
                <p class="unavailable">„Odzyskane po awarii" niedostępne — poza zakresem ExactlyOnceEngine (ADR-0034).</p>
            </section>

            <section class="dashboard-section">
                <h3>Adaptery</h3>
                <table>
                    <thead><tr><th>Adapter</th><th>Okres</th><th>Wysłane</th><th>Odebrane</th><th>Błędy</th></tr></thead>
                    <tbody>
                        ${snapshots.map((snapshot) => `
                            <tr>
                                <td>${snapshot.adapterId}</td>
                                <td>${snapshot.periodStart} – ${snapshot.periodEnd}</td>
                                <td>${snapshot.messagesSent}</td>
                                <td>${snapshot.messagesReceived}</td>
                                <td>${snapshot.errorCount}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
                <p class="unavailable">„Średni czas obsługi" niedostępny — MetricsSnapshot nie śledzi czasu przetwarzania.</p>
            </section>

            <section class="dashboard-section">
                <h3>Wydajność</h3>
                <p>CPU: ${resources.cpuUsagePercent ?? 'niedostępne'} · RAM: ${resources.ramUsedBytes ?? 'niedostępne'}/${resources.ramTotalBytes ?? 'niedostępne'}</p>
            </section>

            <section class="dashboard-section">
                <h3>Operacje</h3>
                <button id="statistics-export">Eksport raportów</button>
            </section>
        `;

        document.getElementById('statistics-export').addEventListener('click', () => {
            const report = { snapshots, exactlyOnce, resources, exportedAt: new Date().toISOString() };
            const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = 'midomail-statistics-report.json';
            link.click();
            URL.revokeObjectURL(url);
        });
    }

    document.getElementById('statistics-filter-apply').addEventListener('click', () => {
        const adapterId = document.getElementById('statistics-adapter-filter').value;
        const from = document.getElementById('statistics-from').value ? new Date(document.getElementById('statistics-from').value) : null;
        const to = document.getElementById('statistics-to').value ? new Date(document.getElementById('statistics-to').value) : null;

        const filtered = allSnapshots.filter((snapshot) => {
            if (adapterId && snapshot.adapterId !== adapterId) return false;
            const periodStart = new Date(snapshot.periodStart);
            if (from && periodStart < from) return false;
            if (to && periodStart > to) return false;
            return true;
        });
        render(filtered);
    });

    render(allSnapshots);
});
