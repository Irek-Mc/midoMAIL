/**
 * Ekran Użytkownicy i uprawnienia (Iteracja 6.27, 70-Uzytkownicy-i-uprawnienia.md). Reużywa
 * `GET/POST /roles`, `GET/POST /accounts`, `POST /accounts/status|role|reset-password` (Iteracja
 * 6.15) oraz nowy `GET /audit` (Iteracja 6.27 — czysta warstwa odczytu nad generycznym audytem
 * `AdminHttpServer` z Fazy 5, nie nowa zdolność domenowa).
 *
 * §5: pełny model widoczny w tym UI, ale bez rzeczywistego wdrożenia serwerowego
 * wieloosobowego (decyzja 2, Kontekst planu Fazy 6) — działa na jednej instancji
 * deweloperskiej/w harnessie (Iteracja 6.29), nie w produkcyjnym wdrożeniu wieloserwerowym.
 * Android nadal używa uproszczonego modelu jednego administratora.
 */
Router.register('users', async (content) => {
    content.innerHTML = `
        <p class="unavailable">Pełny model wieloużytkownikowy — dla wdrożeń serwerowych (JVM/Linux); na Androidzie zastąpiony uproszczonym modelem jednego administratora. Ten ekran działa na pojedynczej instancji deweloperskiej (bez rzeczywistego wdrożenia produkcyjnego wieloosobowego).</p>

        <h2>Użytkownicy i uprawnienia</h2>

        <section class="dashboard-section">
            <h3>Role</h3>
            <table>
                <thead><tr><th>RoleId</th><th>Nazwa</th><th>Uprawnienia</th></tr></thead>
                <tbody id="roles-body"></tbody>
            </table>
        </section>

        <section class="dashboard-section">
            <h3>Konta</h3>
            <table>
                <thead><tr><th>AccountId</th><th>Login</th><th>Rola</th><th>Status</th><th></th></tr></thead>
                <tbody id="accounts-body"></tbody>
            </table>
            <form id="account-create-form">
                <label for="new-username">Login</label>
                <input type="text" id="new-username" required>
                <label for="new-password">Hasło</label>
                <input type="password" id="new-password" required>
                <label for="new-role">RoleId</label>
                <input type="text" id="new-role" required>
                <button type="submit">Utwórz konto</button>
            </form>
        </section>

        <section class="dashboard-section">
            <h3>Historia logowania</h3>
            <ul id="login-history-list"></ul>
        </section>

        <section class="dashboard-section">
            <h3>Audyt zmian uprawnień</h3>
            <ul id="permission-audit-list"></ul>
        </section>
    `;

    async function loadRoles() {
        const response = await Api.get('/roles');
        const roles = await response.json();
        document.getElementById('roles-body').innerHTML = roles.map((role) => `
            <tr>
                <td>${role.roleId}</td><td>${role.name}</td>
                <td>${role.permissions.map((p) => `${p.area}:${p.level}`).join(', ') || '-'}</td>
            </tr>
        `).join('') || '<tr><td colspan="3">Brak ról</td></tr>';
    }

    async function loadAccounts() {
        const response = await Api.get('/accounts');
        const accounts = await response.json();
        document.getElementById('accounts-body').innerHTML = accounts.map((account) => `
            <tr>
                <td>${account.accountId}</td><td>${account.username}</td><td>${account.roleId}</td><td>${account.status}</td>
                <td>
                    <button onclick="window.__midomailToggleAccountStatus('${account.accountId}', '${account.status === 'ACTIVE' ? 'BLOCKED' : 'ACTIVE'}')">
                        ${account.status === 'ACTIVE' ? 'Zablokuj' : 'Odblokuj'}
                    </button>
                    <button onclick="window.__midomailResetPassword('${account.accountId}')">Reset hasła</button>
                </td>
            </tr>
        `).join('') || '<tr><td colspan="5">Brak kont</td></tr>';
    }

    async function loadAudit() {
        const response = await Api.get('/audit');
        const entries = await response.json();
        document.getElementById('login-history-list').innerHTML = entries
            .filter((entry) => entry.operation.includes('/accounts/login'))
            .map((entry) => `<li>${entry.timestamp} — ${entry.operation}</li>`)
            .join('') || '<li>Brak wpisów</li>';
        document.getElementById('permission-audit-list').innerHTML = entries
            .filter((entry) => entry.operation.includes('/accounts') || entry.operation.includes('/roles'))
            .map((entry) => `<li>${entry.timestamp} — ${entry.operation}</li>`)
            .join('') || '<li>Brak wpisów</li>';
    }

    document.getElementById('account-create-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        await Api.post('/accounts', {
            username: document.getElementById('new-username').value,
            password: document.getElementById('new-password').value,
            roleId: document.getElementById('new-role').value
        });
        document.getElementById('account-create-form').reset();
        await loadAccounts();
        await loadAudit();
    });

    window.__midomailToggleAccountStatus = async (accountId, newStatus) => {
        await Api.post(`/accounts/status?id=${encodeURIComponent(accountId)}&status=${newStatus}`);
        await loadAccounts();
        await loadAudit();
    };

    window.__midomailResetPassword = async (accountId) => {
        const newPassword = window.prompt('Nowe hasło:');
        if (!newPassword) return;
        await Api.postRaw(`/accounts/reset-password?id=${encodeURIComponent(accountId)}`, newPassword);
        await loadAudit();
        window.alert('Hasło zresetowane');
    };

    await loadRoles();
    await loadAccounts();
    await loadAudit();
});
