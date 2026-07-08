/**
 * Klient Admin REST API (SA-12, ADR-0033) — klucz API w sessionStorage (nie localStorage,
 * czyszczony przy zamknięciu karty), dołączany jako nagłówek X-API-Key do każdego fetch().
 * 401 z dowolnego żądania emituje 'midomail:unauthorized' - app.js przekierowuje do logowania.
 *
 * API_BASE_URL: domyślnie ten sam host co :ui-web, port 8080 (domyślny Adapter REST) -
 * konfigurowalne, jeśli wdrożenie serwuje oba z różnych hostów.
 */
const Api = (() => {
    const STORAGE_KEY = 'midomail-api-key';
    const API_BASE_URL = `${window.location.protocol}//${window.location.hostname}:8080`;

    function getApiKey() {
        return sessionStorage.getItem(STORAGE_KEY);
    }

    function setApiKey(key) {
        sessionStorage.setItem(STORAGE_KEY, key);
    }

    function clearApiKey() {
        sessionStorage.removeItem(STORAGE_KEY);
    }

    function isAuthenticated() {
        return getApiKey() !== null;
    }

    async function request(method, path, body, rawBody) {
        const headers = { 'X-API-Key': getApiKey() || '' };
        let requestBody;
        if (rawBody !== undefined) {
            requestBody = rawBody;
        } else if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
            requestBody = JSON.stringify(body);
        }
        const response = await fetch(API_BASE_URL + path, { method, headers, body: requestBody });
        if (response.status === 401) {
            clearApiKey();
            window.dispatchEvent(new CustomEvent('midomail:unauthorized'));
            throw new Error('Unauthorized');
        }
        return response;
    }

    return {
        getApiKey,
        setApiKey,
        clearApiKey,
        isAuthenticated,
        get: (path) => request('GET', path),
        post: (path, body) => request('POST', path, body),
        /** Dla endpointów przyjmujących surowy tekst w ciele (nie JSON) — np. POST /config, POST /adapters/configuration. */
        postRaw: (path, rawBody) => request('POST', path, undefined, rawBody),
        del: (path) => request('DELETE', path)
    };
})();
