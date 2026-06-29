# Smoke Checks

Run a lightweight cross-surface smoke check after starting the backend and frontend locally.

## Quick Run

```bash
npm run smoke
```

Defaults:

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:5173`
- Timeout: `5000ms`

## Environment Options

```bash
SMOKE_BACKEND_URL=http://localhost:8080 \
SMOKE_FRONTEND_URL=http://localhost:5173 \
SMOKE_TIMEOUT_MS=5000 \
npm run smoke
```

Optional authenticated checks:

```bash
SMOKE_AUTH_TOKEN=<access-token> \
SMOKE_USER_ID=<user-id> \
npm run smoke
```

Optional structured admin metrics check:

```bash
SMOKE_CHECK_ADMIN_METRICS=true \
SMOKE_AUTH_TOKEN=<admin-access-token> \
npm run smoke
```

Optional SockJS/WebSocket info check:

```bash
SMOKE_CHECK_WS=true npm run smoke
```

Offline script validation, useful when services are not running:

```bash
SMOKE_ALLOW_OFFLINE=true npm run smoke
```

## What It Checks

- Backend health via `/actuator/health`.
- Backend Prometheus text via `/actuator/prometheus`.
- Whether app endpoint metrics are already present in Prometheus output.
- Frontend Vue app shell HTML.
- Optional authenticated message API when `SMOKE_AUTH_TOKEN` and `SMOKE_USER_ID` are set.
- Optional structured admin endpoint metrics via `/api/admin/performance/endpoints` when `SMOKE_CHECK_ADMIN_METRICS=true` and an admin `SMOKE_AUTH_TOKEN` are set.
- Structured admin metrics include `data.rows`, `data.summary.status`, and `data.summary.recommendations`.
- Structured admin metrics history via `/api/admin/performance/history`, including `data.trend.direction` and `data.samples`.
- Optional SockJS info endpoint when `SMOKE_CHECK_WS=true` is set.
