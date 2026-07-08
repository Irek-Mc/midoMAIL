/**
 * Ekran Monitoring (Iteracja 6.19, 66-Monitoring.md). Reużywa wyłącznie endpointy zbudowane w
 * poprzednich iteracjach (GET /dashboard/status, /adapters, /alerts, /monitoring/resources,
 * POST /alerts/acknowledge) — brak nowych zdolności backendu, ten ekran to wyłącznie warstwa
 * prezentacji nad już istniejącymi kontraktami.
 *
 * „Kolejki przetwarzania" (§2) świadomie niedostępne — ten sam powód co w Dashboardzie (ADR-0034,
 * SchedulerProvider bez introspekcji stanu zadań).
 *
 * „Eksport raportu" (§4) realizowany WYŁĄCZNIE po stronie klienta — zapis już pobranych danych
 * (bez żadnej lokalnej transformacji/wyliczenia) do pliku JSON przez przeglądarkę; nie wymaga
 * nowego endpointu REST.
 */
Router.register('monitoring', async (content) => {
    content.innerHTML = '<p>Ładowanie…</p>';

    const [statusResponse, adaptersResponse, alertsResponse, resourcesResponse] = await Promise.all([
        Api.get('/dashboard/status'),
        Api.get('/adapters'),
        Api.get('/alerts'),
        Api.get('/monitoring/resources')
    ]);

    const status = await statusResponse.json();
    const adapters = await adaptersResponse.json();
    const alerts = await alertsResponse.json();
    const resources = await resourcesResponse.json();

    async function acknowledge(alertId) {
        await Api.post(`/alerts/acknowledge?id=${encodeURIComponent(alertId)}`);
        Router.navigate();
    }
    window.__midomailAcknowledgeAlert = acknowledge;

    function exportReport() {
        const report = { status, adapters, alerts, resources, exportedAt: new Date().toISOString() };
        const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'midomail-monitoring-report.json';
        link.click();
        URL.revokeObjectURL(url);
    }
    window.__midomailExportMonitoringReport = exportReport;

    content.innerHTML = `
        <h2>Monitoring</h2>

        <section class="dashboard-section">
            <h3>Stan Gateway</h3>
            <p>${status.status} · wersja ${status.version}</p>
        </section>

        <section class="dashboard-section">
            <h3>Health komponentów / Dostępność adapterów</h3>
            <table>
                <thead><tr><th>Adapter</th><th>Stan</th><th>Zdrowie</th></tr></thead>
                <tbody>
                    ${adapters.map((adapter) => `
                        <tr><td>${adapter.adapterId}</td><td>${adapter.state}</td><td>${adapter.healthy ? 'zdrowy' : 'niezdrowy'}</td></tr>
                    `).join('')}
                </tbody>
            </table>
        </section>

        <section class="dashboard-section">
            <h3>Kolejki przetwarzania</h3>
            <p class="unavailable">Niedostępne w tej fazie — Scheduler nie udostępnia dziś stanu zadań (ADR-0034).</p>
        </section>

        <section class="dashboard-section">
            <h3>Wydajność</h3>
            <p>
                CPU: ${resources.cpuUsagePercent ?? 'niedostępne'} ·
                RAM: ${resources.ramUsedBytes ?? 'niedostępne'}/${resources.ramTotalBytes ?? 'niedostępne'} ·
                Storage: ${resources.storageUsedBytes ?? 'niedostępne'}/${resources.storageTotalBytes ?? 'niedostępne'} ·
                Network: ${resources.networkBytesReceived ?? 'niedostępne'}/${resources.networkBytesSent ?? 'niedostępne'}
            </p>
        </section>

        <section class="dashboard-section">
            <h3>Aktywne alerty</h3>
            ${alerts.length === 0 ? '<p>Brak aktywnych alertów</p>' : `
                <table>
                    <thead><tr><th>Poziom</th><th>Źródło</th><th>Czas</th><th>Zalecane działanie</th><th></th></tr></thead>
                    <tbody>
                        ${alerts.map((alert) => `
                            <tr>
                                <td>${alert.level}</td>
                                <td>${alert.source}</td>
                                <td>${alert.timestamp}</td>
                                <td>${alert.recommendedAction ?? '-'}</td>
                                <td><button onclick="window.__midomailAcknowledgeAlert('${alert.alertId}')">Potwierdź</button></td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            `}
        </section>

        <section class="dashboard-section">
            <h3>Operacje</h3>
            <a href="#/diagnostics">Przejście do diagnostyki</a> ·
            <a href="#/logs">Przejście do logów</a> ·
            <button onclick="window.__midomailExportMonitoringReport()">Eksport raportu</button>
        </section>
    `;
});
