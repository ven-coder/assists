# API 参考

本目录为 Assists 的 **API 参考** 索引，便于按模块或能力查阅。

## 文档索引

- **Web 能力**：根目录 [WEB_CAPABILITIES.md](../../WEB_CAPABILITIES.md) — 通过 `assistsx` / `assistsxAsync` 等调用的方法列表与约定
- **Native 能力**：根目录 [NATIVE_CAPABILITIES.md](../../NATIVE_CAPABILITIES.md) — 原生侧接口说明（如有）

## 约定说明

- **请求**：通常为 JSON，包含 `method`、`arguments`、`node`/`nodes`、`callbackId` 等
- **响应**：JSON 包含 `code`（0 表示成功）、`data`、`message`、`callbackId`
- **节点**：多数接口支持在根节点或指定 `node`（含 `nodeId`）上操作

后续可按子模块在此目录下新增独立 API 文档（如 `web-bridge.md`、`node-api.md` 等）。
