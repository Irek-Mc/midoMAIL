/**
 * Ekran Dashboard (Iteracja 6.18, 61-Dashboard.md). Każda wartość pochodzi z jawnego kontraktu
 * Core przez Admin REST API (GET /dashboard/status, /adapters, /dashboard/exactly-once,
 * /monitoring/resources, /dashboard/events) — brak lokalnych obliczeń.
 *
 * „Kolejki" (Waiting/Processing/Retrying/Failed wg Schedulera) świadomie niedostępne
 * (ADR-0034) — SchedulerProvider nie ma dziś introspekcji stanu zadań.
 * „Restart Gateway"/„Reload Configuration" (SA-15) świadomie niedostępne — operacje całego
 * procesu, poza zakresem Adaptera REST/CLI (SPEC-0024/0025 znają wyłącznie restart pojedynczego
 * adaptera).
 */
Router.register('dashboard', async (content) => {
    content.innerHTML = '<p>Ładowanie…</p>';

    const [statusResponse, adaptersResponse, exactlyOnceResponse, resourcesResponse, eventsResponse] = await Promise.all([
        Api.get('/dashboard/status'),
        Api.get('/adapters'),
        Api.get('/dashboard/exactly-once'),
        Api.get('/monitoring/resources'),
        Api.get('/dashboard/events')
    ]);

    const status = await statusResponse.json();
    const adapters = await adaptersResponse.json();
    const exactlyOnce = await exactlyOnceResponse.json();
    const resources = await resourcesResponse.json();
    const events = await eventsResponse.json();

    const uptimeHours = (status.uptimeSeconds / 3600).toFixed(1);

    content.innerHTML = `
        <h2>Dashboard</h2>

        <section class="dashboard-section">
            <h3>Status Gateway</h3>
            <p>Stan: <strong>${status.status}</strong> · Wersja: ${status.version} · Czas pracy: ${uptimeHours} h</p>
        </section>

        <section class="dashboard-section">
            <h3>Adaptery</h3>
            <table>
                <thead><tr><th>Nazwa</th><th>Stan</th><th>Zdrowie</th><th>Błędy</th></tr></thead>
                <tbody>
                    ${adapters.map((adapter) => `
                        <tr>
                            <td>${adapter.adapterId}</td>
                            <td>${adapter.state}</td>
                            <td>${adapter.healthy ? 'zdrowy' : 'niezdrowy'}</td>
                            <td>${adapter.errorCount}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </section>

        <section class="dashboard-section">
            <h3>Kolejki</h3>
            <p class="unavailable">Niedostępne w tej fazie — Scheduler nie udostępnia dziś stanu zadań (ADR-0034).</p>
        </section>

        <section class="dashboard-section">
            <h3>Exactly Once</h3>
            <p>Przetworzone: ${exactlyOnce.processed} · Zapobieżone duplikaty: ${exactlyOnce.duplicatesPrevented}</p>
        </section>

        <section class="dashboard-section">
            <h3>Monitoring</h3>
            <p>
                CPU: ${resources.cpuUsagePercent ?? 'niedostępne'} ·
                RAM: ${resources.ramUsedBytes ?? 'niedostępne'}/${resources.ramTotalBytes ?? 'niedostępne'} ·
                Storage: ${resources.storageUsedBytes ?? 'niedostępne'}/${resources.storageTotalBytes ?? 'niedostępne'}
            </p>
        </section>

        <section class="dashboard-section">
            <h3>Zdarzenia</h3>
            <ul>
                ${events.map((event) => `<li>${event.timestamp} — ${event.eventType} (${event.category})</li>`).join('')}
            </ul>
        </section>

        <section class="dashboard-section">
            <h3>Operacje</h3>
            <button disabled title="Niedostępne w tej fazie — operacja całego procesu, poza zakresem Adaptera REST/CLI (SA-15)">Restart Gateway</button>
            <button disabled title="Niedostępne w tej fazie — operacja całego procesu, poza zakresem Adaptera REST/CLI (SA-15)">Reload Configuration</button>
            <a href="#/diagnostics">Diagnostics</a>
            <a href="#/logs">Open Logs</a>
            <a href="#/adapters">Manage Adapters</a>
        </section>
    `;
});
