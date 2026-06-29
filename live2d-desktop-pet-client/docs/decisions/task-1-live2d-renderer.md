# Task 1 Live2D Renderer Decision

## Decision

Use `pixi-live2d-display` as the MVP Live2D renderer for the Tauri/Vue client.

## Evidence

- The client loads `public/live2d/sample/model.model3.json` through `pixi-live2d-display/cubism4` and Pixi 6 in a visible `<canvas>` surface.
- The Cubism runtime script is loaded from `public/vendor/live2d/live2dcubismcore.min.js`, which satisfies the Cubism 3/4 runtime requirement documented by `pixi-live2d-display`.
- The sample model JSON originally existed without its referenced `.moc3`, texture, physics, and motion files in the client; Task 1 copied those referenced assets into `public/live2d/sample/` so the configured model path resolves as a complete model package.
- The app emits structured console logs for both success and failure:
  - `live2d:model-loaded` for `/live2d/sample/model.model3.json`
  - `live2d:model-load-failed` for `/live2d/sample/missing.model3.json`
- `pnpm build` completes with `vue-tsc -b && vite build`, proving TypeScript and production bundling compatibility.

## Fallback Assessment

The official Cubism Web SDK fallback is not selected for the MVP because `pixi-live2d-display` rendered the Cubism model in the Web surface without a compatibility or build failure. Revisit the official SDK only if a later Tauri/WebView2-only QA pass exposes a rendering bug, license constraint, or unsupported model behavior that is not present in the browser/WebGL smoke test.

## Scope Notes

This is intentionally a renderer spike only. It does not add chat, backend WebSocket logic, memory, voice, tools, or permanent Live2D state mapping.
