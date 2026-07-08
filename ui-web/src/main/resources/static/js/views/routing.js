/**
 * Ekran Routing (Iteracja 6.24, 63-Routing.md). Reużywa w pełni istniejące endpointy REST
 * (`GET/POST/DELETE /routing/rules`, `GET /routing/rules/history`, `POST /routing/simulate` —
 * Faza 5/Iteracja 6.12/6.24). Symulator rozszerzony w tej iteracji o `messagePriorityBefore`/
 * `messagePriorityAfter` (63-Routing.md §4).
 */
Router.register('routing', async (content) => {
    content.innerHTML = `
        <h2>Routing</h2>

        <section class="dashboard-section">
            <h3>Reguły</h3>
            <table>
                <thead>
                    <tr>
                        <th>RuleId</th><th>Priorytet</th><th>Włączona</th><th>Kanał źr.</th>
                        <th>TargetChannel</th><th>TargetAdapter</th><th>DeliveryPolicy</th><th>SetPriority</th><th>Wersja</th><th></th>
                    </tr>
                </thead>
                <tbody id="routing-rules-body"></tbody>
            </table>
        </section>

        <section class="dashboard-section">
            <h3>Dodaj / Edytuj regułę</h3>
            <form id="routing-rule-form">
                <input type="hidden" id="rule-editing-id">
                <label for="rule-id">RuleId</label>
                <input type="text" id="rule-id" required>
                <label for="rule-priority">Priorytet</label>
                <input type="number" id="rule-priority" value="100" required>
                <label><input type="checkbox" id="rule-enabled" checked> Włączona</label>
                <label for="rule-source-channel">Kanał źródłowy (warunek)</label>
                <input type="text" id="rule-source-channel">
                <label for="rule-target-channel">TargetChannel</label>
                <input type="text" id="rule-target-channel" required>
                <label for="rule-target-adapter">TargetAdapter</label>
                <input type="text" id="rule-target-adapter" required>
                <label for="rule-delivery-policy">DeliveryPolicy</label>
                <input type="text" id="rule-delivery-policy" value="AT_LEAST_ONCE" required>
                <label for="rule-set-priority">SetPriority (opcjonalne)</label>
                <select id="rule-set-priority">
                    <option value="">(brak)</option>
                    <option value="LOW">LOW</option>
                    <option value="NORMAL">NORMAL</option>
                    <option value="HIGH">HIGH</option>
                    <option value="CRITICAL">CRITICAL</option>
                </select>
                <button type="submit">Zapisz</button>
            </form>
            <p id="routing-form-error" class="error-message" hidden></p>
        </section>

        <section class="dashboard-section">
            <h3>Symulator routingu</h3>
            <form id="routing-simulate-form">
                <label for="simulate-source">Kanał źródłowy</label>
                <input type="text" id="simulate-source" required>
                <label for="simulate-destination">Kanał docelowy</label>
                <input type="text" id="simulate-destination" required>
                <label for="simulate-priority">MessagePriority</label>
                <select id="simulate-priority">
                    <option value="LOW">LOW</option>
                    <option value="NORMAL" selected>NORMAL</option>
                    <option value="HIGH">HIGH</option>
                    <option value="CRITICAL">CRITICAL</option>
                </select>
                <button type="submit">Testuj</button>
            </form>
            <div id="routing-simulate-result"></div>
        </section>

        <section class="dashboard-section">
            <h3>Historia zmian</h3>
            <ul id="routing-history-list"></ul>
        </section>
    `;

    async function loadRules() {
        const response = await Api.get('/routing/rules');
        const rules = await response.json();
        document.getElementById('routing-rules-body').innerHTML = rules.map((rule) => `
            <tr>
                <td>${rule.ruleId}</td>
                <td>${rule.priority}</td>
                <td>${rule.enabled}</td>
                <td>${rule.conditions.sourceChannel ?? '-'}</td>
                <td>${rule.targetChannel}</td>
                <td>${rule.targetAdapter}</td>
                <td>${rule.deliveryPolicy}</td>
                <td>${rule.setPriority ?? '-'}</td>
                <td>${rule.version}</td>
                <td>
                    <button onclick="window.__midomailEditRule('${rule.ruleId}')">Edytuj</button>
                    <button onclick="window.__midomailToggleRule('${rule.ruleId}', ${!rule.enabled})">${rule.enabled ? 'Wyłącz' : 'Włącz'}</button>
                    <button onclick="window.__midomailRemoveRule('${rule.ruleId}')">Usuń</button>
                </td>
            </tr>
        `).join('') || '<tr><td colspan="10">Brak reguł</td></tr>';
        return rules;
    }

    async function loadHistory() {
        const response = await Api.get('/routing/rules/history');
        const history = await response.json();
        document.getElementById('routing-history-list').innerHTML = history.map((change) =>
            `<li>${change.timestamp} — ${change.ruleId} ${change.changeType} (wersja: ${change.version ?? '-'})</li>`
        ).join('') || '<li>Brak historii zmian</li>';
    }

    function ruleDtoFromForm() {
        const setPriority = document.getElementById('rule-set-priority').value;
        return {
            ruleId: document.getElementById('rule-id').value,
            priority: parseInt(document.getElementById('rule-priority').value, 10),
            enabled: document.getElementById('rule-enabled').checked,
            conditions: { sourceChannel: document.getElementById('rule-source-channel').value || null },
            targetChannel: document.getElementById('rule-target-channel').value,
            targetAdapter: document.getElementById('rule-target-adapter').value,
            deliveryPolicy: document.getElementById('rule-delivery-policy').value,
            setPriority: setPriority || null,
            version: "1"
        };
    }

    document.getElementById('routing-rule-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const errorMessage = document.getElementById('routing-form-error');
        errorMessage.hidden = true;
        const editingId = document.getElementById('rule-editing-id').value;
        const dto = ruleDtoFromForm();
        const response = editingId
            ? await Api.post(`/routing/rules/update?ruleId=${encodeURIComponent(editingId)}`, dto)
            : await Api.post('/routing/rules', dto);
        if (!response.ok) {
            errorMessage.textContent = await response.text();
            errorMessage.hidden = false;
            return;
        }
        document.getElementById('routing-rule-form').reset();
        document.getElementById('rule-editing-id').value = '';
        await loadRules();
        await loadHistory();
    });

    window.__midomailEditRule = async (ruleId) => {
        const rules = await loadRules();
        const rule = rules.find((r) => r.ruleId === ruleId);
        if (!rule) return;
        document.getElementById('rule-editing-id').value = rule.ruleId;
        document.getElementById('rule-id').value = rule.ruleId;
        document.getElementById('rule-priority').value = rule.priority;
        document.getElementById('rule-enabled').checked = rule.enabled;
        document.getElementById('rule-source-channel').value = rule.conditions.sourceChannel ?? '';
        document.getElementById('rule-target-channel').value = rule.targetChannel;
        document.getElementById('rule-target-adapter').value = rule.targetAdapter;
        document.getElementById('rule-delivery-policy').value = rule.deliveryPolicy;
        document.getElementById('rule-set-priority').value = rule.setPriority ?? '';
    };

    window.__midomailToggleRule = async (ruleId, enable) => {
        const rules = await loadRules();
        const rule = rules.find((r) => r.ruleId === ruleId);
        if (!rule) return;
        await Api.post(`/routing/rules/update?ruleId=${encodeURIComponent(ruleId)}`, { ...rule, enabled: enable });
        await loadRules();
        await loadHistory();
    };

    window.__midomailRemoveRule = async (ruleId) => {
        await Api.del(`/routing/rules?ruleId=${encodeURIComponent(ruleId)}`);
        await loadRules();
        await loadHistory();
    };

    document.getElementById('routing-simulate-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const response = await Api.post('/routing/simulate', {
            sourceChannel: document.getElementById('simulate-source').value,
            destinationChannel: document.getElementById('simulate-destination').value,
            messagePriority: document.getElementById('simulate-priority').value
        });
        const decision = await response.json();
        document.getElementById('routing-simulate-result').innerHTML = decision.matched
            ? `<p>Dopasowano: ${decision.targetChannel} → ${decision.targetAdapter} (${decision.deliveryPolicy}). MessagePriority: ${decision.messagePriorityBefore} → ${decision.messagePriorityAfter}</p>`
            : `<p>Brak dopasowania. MessagePriority: ${decision.messagePriorityBefore}</p>`;
    });

    await loadRules();
    await loadHistory();
});
