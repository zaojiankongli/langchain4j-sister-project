# Phase 1 微信小程序认证与邮箱绑定协议

## 目标

小程序端使用微信身份进入系统，但系统账号仍以邮箱用户为主账号。Phase 1 要打通：

```text
wx.login → 微信身份识别 → bindToken → 邮箱验证码绑定 → 业务 accessToken → 资料完善/首页
```

## Source of Truth

微信身份绑定唯一可信表：`user_wechat_bindings`。

`users.wx_*` 字段仅作为历史兼容/冗余字段，不参与以下判断：

- 微信是否已绑定用户
- 微信登录查找用户
- 微信绑定冲突判断
- 解绑/换绑判断

核心唯一约束：

```sql
UNIQUE (wechat_appid, openid)
UNIQUE (user_id, wechat_appid)
```

含义：

- 一个微信 openid 在同一个小程序 appid 下只能绑定一个 user。
- 一个 user 在同一个小程序 appid 下只能绑定一个微信。

## Token 概念

| 名称 | 来源 | 用途 | 是否给前端 |
|---|---|---|---|
| `js_code` | 小程序 `wx.login()` | 后端换取 openid/session_key | 前端提交给后端 |
| `openid` | 微信 `jscode2session` | 微信身份标识 | 不直接暴露为绑定依据 |
| `session_key` | 微信 `jscode2session` | 解密/校验微信加密数据 | 不给前端 |
| `bindToken` | 后端生成 | 未绑定微信身份的临时绑定凭证 | 给前端 |
| `accessToken` | 本系统后端 JWT | 访问本系统业务 API | 给前端 |
| `refreshToken` | 本系统后端 JWT/session | 刷新业务登录态 | 给前端 |

## Redis 临时绑定上下文

Key：

```text
auth:wx:bind:{bindToken}
```

Value：

```text
{wechatAppid}|{openid}|{unionid}
```

TTL：10 分钟。

作用：邮箱绑定时，后端通过 `bindToken` 找回此前由微信接口验证过的 `openid`，避免前端直接提交 openid。

## 接口 1：微信身份识别

```http
POST /api/auth/wx-login
```

### 请求

```json
{
  "code": "wx.login 返回的 code"
}
```

### 后端流程

```text
1. 校验 code。
2. 调用微信 jscode2session。
3. 获取 openid / unionid。
4. 查 user_wechat_bindings by wechat_appid + openid + bound。
5. 如果找到绑定：
   5.1 查 users。
   5.2 更新 user_wechat_bindings.last_login_at。
   5.3 更新 users.last_active_at。
   5.4 签发本系统 accessToken / refreshToken。
   5.5 按资料完整度返回 LOGGED_IN 或 PROFILE_INCOMPLETE。
6. 如果未找到绑定：
   6.1 生成 bindToken。
   6.2 Redis 保存 bindToken -> appid/openid/unionid。
   6.3 返回 WECHAT_UNBOUND。
```

### 返回：微信未绑定

```json
{
  "status": "WECHAT_UNBOUND",
  "bindToken": "temporary-bind-token",
  "expiresIn": 600,
  "nextAction": "BIND_EMAIL",
  "requiresEmailBind": true,
  "emailBound": false,
  "isNewUser": true
}
```

### 返回：已绑定且资料完整

```json
{
  "status": "LOGGED_IN",
  "accessToken": "backend-access-token",
  "refreshToken": "backend-refresh-token",
  "user": {
    "id": "user-id",
    "email": "user@example.com",
    "username": "nickname",
    "aiType": 2
  },
  "requiresProfileComplete": false,
  "isNewUser": false,
  "emailBound": true,
  "emailExists": true,
  "accountType": "existing"
}
```

### 返回：已绑定但资料未完善

```json
{
  "status": "PROFILE_INCOMPLETE",
  "accessToken": "backend-access-token",
  "refreshToken": "backend-refresh-token",
  "user": {
    "id": "user-id",
    "email": "user@example.com"
  },
  "requiresProfileComplete": true,
  "isNewUser": false,
  "emailBound": true,
  "emailExists": true,
  "accountType": "existing"
}
```

## 接口 2：发送邮箱验证码

```http
POST /api/auth/send-code
```

### 请求

```json
{
  "email": "user@example.com"
}
```

### 返回

```json
{
  "code": 200,
  "message": "success"
}
```

## 接口 3：微信绑定邮箱

```http
POST /api/auth/bind-email
```

### 请求

```json
{
  "bindToken": "temporary-bind-token",
  "email": "user@example.com",
  "code": "123456"
}
```

### 后端流程

```text
1. 校验 bindToken 存在。
2. 从 Redis 解析 wechatAppid/openid/unionid。
3. 校验邮箱验证码。
4. 查当前 openid 是否已绑定其它 user。
5. 查 email 对应 user。
6. 如果 user 不存在：创建 user。
7. 如果 user 存在：检查该 user 是否已绑定当前 appid 下其它微信。
8. 插入 user_wechat_bindings。
9. 删除 bindToken。
10. 更新 users.last_active_at。
11. 签发本系统 accessToken / refreshToken。
12. 按资料完整度返回 LOGGED_IN 或 PROFILE_INCOMPLETE。
```

### 返回

同 `/api/auth/wx-login` 的 `LOGGED_IN` / `PROFILE_INCOMPLETE`。

## 冲突规则

### 当前微信已绑定其它账号

```json
{
  "code": 409,
  "message": "该微信已绑定其他账号"
}
```

### 当前邮箱已绑定其它微信

```json
{
  "code": 409,
  "message": "该邮箱已绑定其他微信账号"
}
```

### bindToken 过期

```json
{
  "code": 401,
  "message": "登录状态已失效，请重新登录"
}
```

前端行为：提示用户重新点击微信登录。

## 前端状态行为

| 后端状态 | 小程序行为 |
|---|---|
| `WECHAT_UNBOUND` | 不保存 token；保存 `bindToken` 到页面状态；进入邮箱绑定步骤 |
| `LOGGED_IN` | 保存 accessToken/refreshToken/user；`emailBound=true`；`profileComplete=true`；进首页 |
| `PROFILE_INCOMPLETE` | 保存 accessToken/refreshToken/user；`emailBound=true`；`profileComplete=false`；跳资料完善页 |
| `409` | toast 显示错误；停留绑定页 |
| `401` bindToken 过期 | toast 提示重新微信登录 |

## 联调用例

### Case 1：新微信 + 新邮箱

预期：创建 `users`，创建 `user_wechat_bindings`，返回 `PROFILE_INCOMPLETE`。

### Case 2：新微信 + 已有网页端邮箱

预期：不创建 `users`，创建 `user_wechat_bindings` 指向已有 user，返回 `LOGGED_IN` 或 `PROFILE_INCOMPLETE`。

### Case 3：已绑定微信再次登录

预期：直接返回 `LOGGED_IN` 或 `PROFILE_INCOMPLETE`，不要求邮箱绑定。

### Case 4：微信已绑定其它账号

预期：返回 409，不插入绑定，不签发 token。

### Case 5：邮箱已绑定其它微信

预期：返回 409，不插入绑定，不签发 token。

### Case 6：bindToken 过期

预期：返回 401，前端重新执行 `wx.login`。

## 配置要求

后端必须配置：

```yaml
app:
  wechat:
    appid: ${WECHAT_APPID}
    secret: ${WECHAT_SECRET}
    code-to-session-url: https://api.weixin.qq.com/sns/jscode2session
```

开发环境可使用环境变量覆盖占位值，真实密钥不要提交到仓库。
