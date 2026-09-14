# DU Task Spec — DU-BE-705

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-705
- Change ID: CHG-0017
- Feature Path: 商城前台/商品浏览/库存状态展示/SKU 可售状态聚合
- 权威来源: story-design.md §5 / DU-BE-705

## 任务清单

- [ ] 任务 1 — inventory internal availability：一次 IN 批量 SQL，无记录=0，凭证 401（verifies: TC-001, TC-006）
- [ ] 任务 2 — product 公开端点 + Client 一次调用（断言无 N+1）（verifies: TC-002）
- [ ] 任务 3 — 阈值常量 10 三态映射（0/1-9/≥10）（verifies: TC-003）
- [ ] 任务 4 — 白名单 DTO（仅 skuId+stockStatus）（verifies: TC-004）
- [ ] 任务 5 — 批量校验（空/101/非法 → 400）（verifies: TC-006）
- [ ] 任务 6 — inventory 5xx/超时 → 200 + UNKNOWN 降级（verifies: TC-005）

## Acceptance Criteria

- [ ] AC-015 — 50 skuId 内部一次批量 SQL；公开仅一次 inventory 调用；无记录=0；空/101/非法 400；内部无凭证 401。
- [ ] AC-016 — 0=OUT_OF_STOCK、1-9=LOW_STOCK、≥10=IN_STOCK；公开响应无精确数字。
- [ ] AC-017 — inventory 故障/超时 HTTP 200 且条目 UNKNOWN。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3/4 → 5/6。

## 并行度（Parallelization）

任务 1（inventory）与任务 2 壳（product Controller/校验）并行。

## Verification

- Unit: 阈值边界 0/1/9/10/11；白名单序列化字段断言；批量校验。
- Integration: MockRestServiceServer 模拟一次调用并断言调用次数=1；5xx/超时降级 UNKNOWN；无键=0。
- API: 公开 200 三态、400；internal 无凭证 401、外网经网关 404。
- Migration: N/A。
- Error Case: 对端不可用不向上游 5xx（除 400 参数错）。
