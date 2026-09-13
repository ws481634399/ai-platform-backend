# DU Task Spec — DU-BE-306

> 权威 DU 划分：story-design.md §5 / DU-BE-306
> verifies 绑定 TC-NNN（定义在 Story test-design.md）

## 0. 元信息

- DU id: DU-BE-306
- Change ID: CHG-0012
- Feature Path: 商品与库存/商品发布与商城查询/发布与状态/商品上下架与发布
- 权威来源: story-design.md §5 / DU-BE-306

## 任务清单

- [ ] 任务 1 — mall-identity V5 注册 product:product:publish 权限码（verifies: TC-008）
- [ ] 任务 2 — Product 聚合 publish/unpublish + 上架校验 + 领域事件（verifies: TC-001, TC-002, TC-003, TC-004, TC-007）
- [ ] 任务 3 — ProductApplicationService.publish/unpublish（verifies: TC-005, TC-006）
- [ ] 任务 4 — ProductAdminController publish/unpublish 端点 + 权限注解（verifies: TC-008）

## Acceptance Criteria

- [ ] AC-001 — DRAFT 商品满足条件上架成功，ON_SALE
- [ ] AC-002 — 无主图上架拒绝
- [ ] AC-003 — 无 ENABLED SKU 上架拒绝
- [ ] AC-004 — DISABLED 上架拒绝
- [ ] AC-005 — ON_SALE 下架成功，OFF_SALE
- [ ] AC-006 — OFF_SALE 重新上架成功
- [ ] AC-007 — 上架/下架注册领域事件
- [ ] AC-008 — 无权限 403
- [ ] AC-009 — 前端上下架按钮（DU-FE-304 承接）

## 执行顺序

1. 任务 1 → 2 → 3 → 4

## 并行度

无（串行）

## Verification

- Unit: Product 聚合 publish/unpublish 行为测试
- API: ProductAdminApiTest（MockMvc + 安全切片）
- Error Case: 校验失败 INVALID_ARGUMENT、权限 403
