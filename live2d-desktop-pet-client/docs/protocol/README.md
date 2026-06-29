# Protocol Artifacts

The MVP WebSocket semantic protocol is defined in `pet-ws-v1.md`.

## Validation

Run from the client repository root:

```bash
pnpm protocol:validate
```

The command validates every JSON fixture under `docs/protocol/fixtures/`. `valid-*.json` fixtures must pass, and `invalid-*.json` fixtures must fail with structured protocol error codes such as `UNKNOWN_MESSAGE_TYPE`.
