# DU Task Spec — DU-BE-401

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-401 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-401 与同目录 task-design.md（"怎么做"）配对互链。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-401
- Change ID: CHG-0014
- Feature Path: 商品与库存/商品与 SKU 管理/商品主数据/商品 SPU 管理
- 权威来源: story-design.md §5 / DU-BE-401

## 任务清单

- [x] 任务 1 — 扩展 Product 原子创建契约与事务（verifies: TC-001, TC-002）
- [x] 任务 2 — 添加 Mall/Internal Product Gateway 路由与授权（verifies: TC-005, TC-006）
- [x] 任务 3 — 限制 Inventory Internal 写接口为 SERVICE（verifies: TC-007）

## Acceptance Criteria

- [x] AC-001 — 合法 Product+SKU 一次创建成功。
- [x] AC-002 — SKU 错误导致整体事务回滚。
- [x] AC-005 — 匿名 mall product 网关路由可达。
- [x] AC-006 — 已认证 internal product 网关路由可达。
- [x] AC-007 — Inventory internal 只接受 SERVICE。

## 执行顺序（Execution Order）

<!-- 任务执行先后顺序；跨 DU 顺序须服从权威表 depends on，不得抢跑。 -->

1. 任务 1：冻结后端创建契约。
2. 任务 2：补齐网关路由。
3. 任务 3：收紧内部库存授权。

## 并行度（Parallelization）

<!-- 可并行的任务分组（可空，写"无"表示全串行）；不得违反权威表依赖图（无环）。 -->

无

## Verification

<!-- 必填：逐项给出验证方式与证据位置（测试文件/命令/报告路径）。 -->

- Unit: mall-product 聚合与应用服务测试。
- Integration: `mvn -pl mall-services/mall-product,mall-services/mall-inventory,mall-gateway -am test`。
- API: ProductAdmin/Mall/Internal Controller 及 Security MockMvc/WebTestClient 测试。
- Migration: N/A，无数据库迁移。
- Error Case: 空 SKU、非法 SKU、anonymous/ADMIN 内部库存调用。
