# Pet WebSocket Semantic Protocol v1

This directory defines the MVP WebSocket message contract between the Java AI backend and the Tauri/Vue desktop pet client.

## Validation Command

Run from the client repository root:

```bash
pnpm protocol:validate
```

To validate one fixture directly:

```bash
node docs/protocol/validate-protocol.mjs docs/protocol/fixtures/valid-assistant-delta.json
node docs/protocol/validate-protocol.mjs docs/protocol/fixtures/invalid-unknown-type.json
```

The validator checks every `docs/protocol/fixtures/*.json` file by default. Files whose name starts with `valid-` must pass. Files whose name starts with `invalid-` must fail with a structured protocol error code. The unknown-type fixture fails with `UNKNOWN_MESSAGE_TYPE`.

## Goals

- Keep the backend/client contract semantic and versioned.
- Keep Live2D renderer details inside the client mapping layer.
- Support MVP text streaming, pet reactions, guarded tool confirmation, and structured protocol errors.
- Avoid voice, memory, plugin, and local tool execution design beyond what the MVP message contract requires.

## Envelope

Every message is a JSON object with this envelope:

```json
{
  "version": "1",
  "type": "assistant.message.delta"
}
```

- `version` is the protocol major version and is exactly `"1"` for this contract.
- `type` is one of the message types listed below.
- Unknown `type` values are invalid and must be handled as an `error` event with `code: "UNKNOWN_MESSAGE_TYPE"`, not as a client or backend crash.
- Unsupported `version` values must be handled as `code: "UNSUPPORTED_VERSION"`.

## Direction

Client to Java backend:

- `user.message`
- `tool.confirmed`
- `tool.rejected`

Java backend to client:

- `assistant.message.delta`
- `assistant.message.done`
- `pet.expression`
- `pet.motion`
- `tool.request`
- `error`

## Message Types

### `user.message`

Client sends user text to the Java backend.

```json
{
  "version": "1",
  "type": "user.message",
  "conversationId": "default",
  "messageId": "msg-001",
  "text": "Hello pet"
}
```

### `assistant.message.delta`

Backend streams assistant text chunks.

```json
{
  "version": "1",
  "type": "assistant.message.delta",
  "conversationId": "default",
  "messageId": "msg-001",
  "text": "你好呀，"
}
```

### `assistant.message.done`

Backend marks the stream complete for a message.

```json
{
  "version": "1",
  "type": "assistant.message.done",
  "conversationId": "default",
  "messageId": "msg-001"
}
```

### `pet.expression`

Backend requests a semantic expression. The client maps the semantic name to model-specific Live2D expression assets or parameters locally.

```json
{
  "version": "1",
  "type": "pet.expression",
  "expression": "happy",
  "intensity": 0.8,
  "durationMs": 3000
}
```

Allowed MVP expressions: `neutral`, `happy`, `sad`, `surprised`, `thinking`, `error`.

### `pet.motion`

Backend requests a semantic motion. The client maps the semantic name to model-specific motion groups or files locally.

```json
{
  "version": "1",
  "type": "pet.motion",
  "motion": "wave",
  "priority": "normal"
}
```

Allowed MVP motions: `idle`, `greet`, `wave`, `nod`, `shake_head`, `thinking`, `speaking`.

### `tool.request`

Backend requests a guarded local action. MVP tool requests require explicit user confirmation and do not execute on the backend.

```json
{
  "version": "1",
  "type": "tool.request",
  "toolCallId": "tool-001",
  "tool": "open_url",
  "args": {
    "url": "https://example.com"
  },
  "requiresConfirmation": true,
  "message": "要帮你打开这个网页吗？"
}
```

MVP allows only `open_url`. `requiresConfirmation` is required and must be `true`.

### `tool.confirmed`

Client tells the backend the user approved a requested tool action.

```json
{
  "version": "1",
  "type": "tool.confirmed",
  "toolCallId": "tool-001"
}
```

### `tool.rejected`

Client tells the backend the user rejected or the client denied a requested tool action.

```json
{
  "version": "1",
  "type": "tool.rejected",
  "toolCallId": "tool-001",
  "reason": "user_rejected"
}
```

Allowed reasons: `user_rejected`, `permission_denied`, `timeout`.

### `error`

Either side can report a structured protocol error.

```json
{
  "version": "1",
  "type": "error",
  "code": "UNKNOWN_MESSAGE_TYPE",
  "message": "unknown message type: pet.live2d.setParameter",
  "correlationId": "msg-001"
}
```

MVP error codes: `INVALID_MESSAGE`, `UNKNOWN_MESSAGE_TYPE`, `UNSUPPORTED_VERSION`, `TOOL_NOT_ALLOWED`, `INTERNAL_ERROR`.

## Semantic Boundary

Backend messages must not include renderer-specific Live2D parameter names, model asset paths, motion file paths, expression file names, or WebGL implementation details. For example, `pet.motion.motion` is `"wave"`, not `"/live2d/sample/motions/wave.motion3.json"`; `pet.expression.expression` is `"happy"`, not `"ParamEyeLOpen"` or any other model parameter.

## Artifacts

- `schemas/envelope.schema.json` defines the common envelope and shared semantic enums.
- `schemas/*` defines one JSON schema per MVP message type plus `pet-ws-v1-message.schema.json` as the registry schema.
- `fixtures/valid-*.json` contains examples expected to pass validation.
- `fixtures/invalid-*.json` contains examples expected to fail with structured protocol errors.
