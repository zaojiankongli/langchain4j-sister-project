# Phase 2 小程序首页总览与设备绑定协议

## 目标

让小程序登录后可以稳定展示首页总览，并完成桌宠设备绑定：

```text
已登录用户 → 首页 summary → 绑定设备 → 查询设备状态 → 首页展示设备/最近回复
```

## 接口列表

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/miniprogram/home/summary` | GET | 首页总览 |
| `/api/miniprogram/device/bind` | POST | 绑定桌宠设备码 |
| `/api/miniprogram/device/status` | GET | 查询当前用户设备状态 |

以上接口均需要业务 `Authorization: Bearer {accessToken}`。

## 设备绑定 Source of Truth

设备绑定表：`user_devices`。

MVP 规则：

- 一个设备码只能绑定一个用户。
- 一个用户 MVP 阶段只绑定一个当前设备。
- 再次绑定同一用户时，更新设备码/昵称/状态。

## 接口 1：首页总览

```http
GET /api/miniprogram/home/summary
```

### 返回：未绑定设备

```json
{
  "petName": "未绑定桌宠",
  "deviceName": "--",
  "connectionStatus": "未绑定",
  "mood": "平静",
  "unreadCount": 0,
  "lastReply": "先完成设备绑定，然后就可以开始聊天。",
  "syncAt": "2026-06-07T18:30:00"
}
```

### 返回：已绑定设备

```json
{
  "petName": "Sister",
  "deviceName": "Sister",
  "connectionStatus": "离线",
  "mood": "平静",
  "unreadCount": 0,
  "lastReply": "最近一条 AI 回复",
  "syncAt": "2026-06-07T18:30:00"
}
```

## 接口 2：绑定设备

```http
POST /api/miniprogram/device/bind
```

### 请求

```json
{
  "deviceCode": "PET-2026-0001",
  "nickname": "Sister"
}
```

### 返回

```json
{
  "deviceCode": "PET-2026-0001",
  "nickname": "Sister",
  "status": "离线",
  "boundAt": "2026-06-07T18:30:00"
}
```

## 接口 3：查询设备状态

```http
GET /api/miniprogram/device/status
```

### 返回：未绑定

```json
{
  "deviceCode": null,
  "nickname": null,
  "status": "未绑定"
}
```

### 返回：已绑定

```json
{
  "deviceCode": "PET-2026-0001",
  "nickname": "Sister",
  "status": "离线",
  "boundAt": "2026-06-07T18:30:00"
}
```

## 冲突规则

### 设备码已绑定其它用户

```json
{
  "code": 409,
  "message": "该设备码已绑定其他账号"
}
```

## 小程序行为

| 状态 | 行为 |
|---|---|
| `未绑定` | 首页显示未绑定，引导绑定设备 |
| `离线` | 首页显示设备已绑定但离线 |
| `在线` | 首页显示在线 |
| 409 | 绑定页 toast 显示错误 |

## 联调用例

1. 已登录但未绑定设备：首页显示“未绑定桌宠”。
2. 输入新设备码绑定：返回设备信息，绑定页展示“离线”。
3. 重新进入首页：summary 展示设备昵称和状态。
4. 设备码被其它用户占用：返回 409。
5. 刷新设备状态：绑定页展示后端最新状态。
