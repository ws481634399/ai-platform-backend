# DU Task Spec — DU-BE-803

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-803
- Change ID: CHG-0018
- Feature Path: 商城前台/购物车/游客购物车与合并/游客购物车与登录合并
- 权威来源: story-design.md §5 / DU-BE-803

## 任务清单

- [ ] 任务 1 — merge-token 签发（SET EX 300 NX）与 TTL 断言（verifies: TC-011）
- [ ] 任务 2 — 合并前 sku/batch 预校验（失效 dropped SKU_NOT_SALABLE，其余合并）（verifies: TC-004）
- [ ] 任务 3 — cart_merge.lua：token GET 校验+DEL 消费、同 SKU 数量相加（2+1=3）（verifies: TC-002, TC-005）
- [ ] 任务 4 — 截断 999 truncated.finalQuantity；超 100 dropped；合并后续 90 天 TTL（verifies: TC-003, TC-011）
- [ ] 任务 5 — 重放 401/400 不二次累加；过期 400 可重取；伪造 401（verifies: TC-005）
- [ ] 任务 6 — 公开 POST /api/mall/skus/items：仅 ON_SALE+ENABLED、≤100、匿名 200（verifies: TC-010）

## Acceptance Criteria

- [ ] AC-015 — 游客车行展示公开快照端点可用（仅有效项、匿名、≤100）。
- [ ] AC-016 — 同 SKU 数量相加；异 SKU 并入。
- [ ] AC-017 — 超 999 截断 truncated；超 100 条目 dropped；失效条目 dropped(SKU_NOT_SALABLE)。
- [ ] AC-018 — 单 Lua 原子+一次性 token：重放/伪造拒绝且不累加；token TTL 300；合并后车 TTL 90 天。

## 执行顺序（Execution Order）

1. 任务 1 → 2/6 → 3 → 4 → 5。

## 并行度（Parallelization）

任务 6（公开 items，product 侧）与任务 1-5（cart 侧）并行。

## Verification

- Unit: Lua 返回码与截断/dropped 分类（Testcontainer redis）；预校验分类。
- Integration: 完整合并 E2E（游客 2+会员 1、998+2、101 条目、失效混合、连提两次）；TTL 断言。
- API: 200/400/401；items 过滤与匿名。
- Migration: N/A。
- Error Case: token 过期可重取不 500；product 故障 → 503 不合并且 token 不消费（预校验在 Lua 前失败）。
