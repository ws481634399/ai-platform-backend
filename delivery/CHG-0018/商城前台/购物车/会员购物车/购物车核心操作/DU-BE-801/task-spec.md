# DU Task Spec — DU-BE-801

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-801
- Change ID: CHG-0018
- Feature Path: 商城前台/购物车/会员购物车/购物车核心操作
- 权威来源: story-design.md §5 / DU-BE-801

## 任务清单

- [ ] 任务 1 — mall-cart 依赖/配置补齐 + Redis key/序列化/TTL 常量（verifies: TC-001）
- [ ] 任务 2 — cart_add.lua（累加、999/100 上限、写即续 TTL）+ 加购服务（verifies: TC-001, TC-002, TC-004, TC-011）
- [ ] 任务 3 — product internal sku/batch（双状态/价/图/规格，≤100，401）（verifies: TC-010）
- [ ] 任务 4 — 加购校验：下架/禁用/不存在/非法数量 → 400 且车不变（verifies: TC-003）
- [ ] 任务 5 — PUT 改量/DELETE 单条批删（幂等）（verifies: TC-005）
- [ ] 任务 6 — 单选/全选与跨会话一致（服务端存储）（verifies: TC-006）
- [ ] 任务 7 — 归属安全（401/忽略 body memberId/ADMIN 403）（verifies: TC-007）
- [ ] 任务 8 — M4 selected-items 预留端点（凭证）（verifies: TC-009）
- [ ] 任务 9 — 依赖审计：无 inventory lock 路径（verifies: TC-008）

## Acceptance Criteria

- [ ] AC-001 — 加购写入 Hash；TTL≈7776000；每次写续期。
- [ ] AC-002 — 同 SKU 累加；超 999 400 且保持 999。
- [ ] AC-003 — 不可售/非法数量 400 且车不变。
- [ ] AC-004 — 100 条目成功，101 → 400 CART_ITEMS_LIMIT。
- [ ] AC-005 — 改量/删除/批删幂等正确。
- [ ] AC-006 — 选择状态服务端存储，跨会话一致。
- [ ] AC-007 — 401/403/主体隔离；internal selected-items 凭证校验。
- [ ] AC-014 — mall-cart 无库存锁定调用路径。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3/4 → 5/6 → 7/8/9（审计可后置）。

## 并行度（Parallelization）

任务 3（product 端点，可先 mock）与任务 2 并行；任务 8 独立可并行。

## Verification

- Unit: Lua 行为可嵌入式 redis（Testcontainers）断言 HLEN/数量/TTL；参数校验矩阵。
- Integration: Redis Testcontainer；product batch MockRestServiceServer；跨会话双 token。
- API: 200/204/400/401/403 契约；TTL 断言 ±容差。
- Migration: N/A（无 DB 迁移）。
- Error Case: Redis 故障 → 503 不产生半写（Lua 原子）；product 不可用 → 503 不加购。
