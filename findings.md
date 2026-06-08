# Backend Reuse Findings

## High-confidence reusable backend modules

### Authentication
- `com.zjkl.auth.controller.AuthController`
- `com.zjkl.auth.service.AuthService`
- Existing routes already match the mini program flow:
  - `POST /api/auth/send-code`
  - `POST /api/auth/login`
  - `POST /api/auth/wx-login`
  - `POST /api/auth/bind-email`
  - `POST /api/auth/sync-email-account`
  - `POST /api/auth/refresh`
  - `POST /api/auth/logout`
  - `POST /api/auth/complete-profile`

### Mini program business endpoints
- `com.zjkl.miniprogram.controller.MiniprogramController`
- `com.zjkl.miniprogram.service.MiniprogramService`
- Existing routes already reusable:
  - `GET /api/miniprogram/home/summary`
  - `POST /api/miniprogram/device/bind`
  - `GET /api/miniprogram/device/status`
  - `POST /api/miniprogram/chat/send`
  - `GET /api/miniprogram/chat/history`

### User profile
- `com.zjkl.user.controller.UserProfileController`
- `com.zjkl.user.service.impl.UserProfileServiceImpl`
- `com.zjkl.user.service.UserProfileManageService`
- Existing routes already reusable:
  - `GET /api/user/profile`
  - `PUT /api/user/profile`
  - `PUT /api/user/basic`
  - `PUT /api/user/hobbies`
  - `PUT /api/user/ai-type`
  - `POST /api/user/avatar`

### Chat history
- `com.zjkl.ai.chat.controller.MessageController`
- `com.zjkl.ai.chat.service.ConverMessageService`
- Existing routes reusable for richer history views:
  - `GET /api/messages/{userId}`
  - `GET /api/messages/{userId}/latest`
  - `GET /api/messages/{userId}/by-date`
  - `GET /api/messages/{userId}/sessions`

### Files and mail
- `com.zjkl.ai.oss.controller.OssController`
- `com.zjkl.ai.oss.service.OssService`
- `com.zjkl.mail.controller.MailController`

## Gaps still requiring backend work

### Avatar flow
- The database schema already contains `users.avatar_url`.
- Existing reusable avatar persistence/upload flow is already present:
  - `UserProfileController.uploadAvatar()` -> `UserProfileService.uploadAvatar()` -> `OssService.uploadAvatar()`
  - `UserProfileManageService.completeProfile()` also accepts `avatarUrl` during first-login onboarding.
- Therefore the avatar work is an adapter/flow unification task, not a new schema task.

### Mini program upload endpoints
- Missing explicit adapters for:
  - `POST /api/miniprogram/upload/background`
  - `POST /api/miniprogram/upload/voice`
- Avatar upload exists in the shared user area and can be reused via adapter.
- `OssService` already provides `uploadVoice(userId, MultipartFile)` and `uploadAvatar(userId, MultipartFile)`, so the mini program only needs thin forwarding to the shared OSS layer.

### Chat intelligence
- `MiniprogramService.sendChatMessage()` currently returns a fixed reply.
- If the product needs real companion behavior, this should be upgraded to use the shared AI/chat pipeline instead of a stub response.

### Adapter layer normalization
- The mini program currently expects some `/api/miniprogram/*` paths that overlap with existing `/api/user/*` or `/api/oss/*` capabilities.
- A thin adapter layer is preferable to duplicating business logic.

## Architecture risks
- The same concept appears in multiple areas: auth state, profile state, chat history, and uploads.
- Without explicit core-service extraction, the web app, desktop pet, and mini program will drift apart.
- The safest boundary is: services hold business logic, controllers/adapters handle client differences.
