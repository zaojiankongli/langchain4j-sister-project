# Backend Refactor Plan

## Goal
Refactor the backend within the existing `controller -> service -> mapper` structure so shared business capabilities live once in services, while web, mini program, and desktop pet only use dedicated names when they have dedicated business.

## Current understanding
- The backend repository exists at `C:\Users\饶策\Desktop\langchain4j_sister_backend`.
- The workspace already contains reusable backend controllers/services for auth, user profile, miniprogram, OSS, mail, chat history, emotion, memory, and settings.
- The mini program currently calls a mixture of `/api/auth/*`, `/api/miniprogram/*`, `/api/user/*`, `/api/messages/*`, and `/api/oss/*` routes.
- Some mini program endpoints already exist, while a few are still missing or need thin controller methods over existing services.

## Plan

### Phase 1 — Freeze the target architecture ✅
- Define shared core modules: auth, user, chat, device, file, mail, emotion, memory, settings.
- Keep the traditional package style: controller handles routes, service handles business, mapper handles database access.
- Document naming rules: shared business uses business names; client-specific business uses dedicated client prefixes.

### Phase 2 — Inventory reusable backend endpoints ✅
- Map existing controllers and services to mini program needs.
- Record which endpoints are already implemented and directly reusable.
- Record which endpoints are implemented under different paths and only need thin controller methods.

### Phase 3 — Identify gaps ✅
- Confirm missing mini program endpoints: background upload and voice upload.
- Confirm whether chat needs a true AI reply pipeline or can temporarily keep the current fixed reply.
- Confirm whether web and desktop already have equivalent routes or need adapter controllers.

### Phase 4 — Extract shared business services ✅
- Keep logic inside service classes.
- Avoid duplicating login, profile update, chat history, device status, or upload logic across controllers.
- Treat avatar as a shared capability that persists to `users.avatar_url` and is updated through a single service path.

### Phase 5 — Split or add thin client-specific controller methods ✅
- Add or keep small client-specific controller methods only where a client has a different path or response shape.
- Reuse the same services underneath.
- Normalize request/response DTOs per client while preserving shared domain semantics.
- For avatar, use one canonical upload/update path:
  1. upload file
  2. store in OSS
  3. persist returned URL to `users.avatar_url`
  4. return the URL to the caller
- Keep mini program and web as thin controller methods over the same avatar service; do not create a separate avatar domain.
- Mini program voice uploads should also stay thin: controller → miniprogram service → shared OSS service.

### Phase 6 — Verify consistency ✅
- Ensure the mini program can keep its current flow.
- Ensure web and desktop can reuse the same services without endpoint duplication.
- Ensure no service layer contains client-specific branching.

## Naming rules

- Shared business uses plain business names: `AuthService`, `UserProfileService`, `OssService`, `ConverMessageService`, `SettingsService`.
- Mini program-only business uses `Miniprogram*`: `MiniprogramController`, `MiniprogramService`, `UserDeviceMapper` for mini program device binding.
- Desktop pet-only business uses `Pet*` or `Desktop*`: `PetMessageChatService`, `PetRealtimeStompController`, `DesktopOmniRealtimeSessionService`.
- Web-only business should use `Web*` only when it is truly web-specific. If it is shared, keep the plain business name.
- Do not introduce separate adapter packages unless the existing controller/service/mapper structure is no longer enough.

## Success criteria
- Shared business logic exists once.
- Client-specific endpoints are isolated to thin controller methods or clearly named client-specific services.
- Missing mini program upload endpoints are identified and planned.
- Existing auth/profile/chat/device/file logic is explicitly mapped to reusable backend modules.

## Risks
- Overlapping responsibilities between `AuthController`, `MiniprogramController`, and `UserProfileController`.
- Chat currently appears to return a fixed reply in `MiniprogramService` rather than a full AI response pipeline.
- Mini program paths may differ from existing backend route names, requiring thin controller methods rather than duplicated services.
