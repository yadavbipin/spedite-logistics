/**
 * Keep API requests on the same origin as the web app.
 *
 * In development, `ng serve` forwards `/api` through `proxy.conf.json`.
 * In production, the included nginx configuration forwards it to the backend.
 */
export const API_BASE_URL = '/api';
