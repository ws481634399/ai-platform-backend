# DU Task Spec — DU-BE-308

## 0. 元信息

- DU id: DU-BE-308
- Change ID: CHG-0012
- Feature Path: 商品与库存/商品发布与商城查询/内部契约与快照/内部查询契约与商品快照
- 权威来源: story-design.md §5 / DU-BE-308

## 任务清单

- [ ] 任务 1 — ProductSnapshotView DTO（verifies: TC-001, TC-002）
- [ ] 任务 2 — ProductApplicationService.getSkuSnapshot（verifies: TC-003, TC-004, TC-005）
- [ ] 任务 3 — InternalProductController 端点（verifies: TC-001, TC-003）

## Acceptance Criteria

- [ ] AC-001 — 返回完整 ProductSnapshot 字段
- [ ] AC-002 — 不含领域实体引用
- [ ] AC-003 — 404 场景
- [ ] AC-004 — price 为分
- [ ] AC-005 — skuAttributes 正确

## 执行顺序

1. 任务 1 → 2 → 3

## 并行度

无

## Verification

- API: InternalProductApiTest（MockMvc）
- 静态检查：ProductSnapshotView 不 import 领域类
