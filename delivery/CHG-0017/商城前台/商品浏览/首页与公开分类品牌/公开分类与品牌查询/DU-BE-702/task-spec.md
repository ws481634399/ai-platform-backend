# DU Task Spec — DU-BE-702

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-702
- Change ID: CHG-0017
- Feature Path: 商城前台/商品浏览/首页与公开分类品牌/公开分类与品牌查询
- 权威来源: story-design.md §5 / DU-BE-702

## 任务清单

- [ ] 任务 1 — categories/tree：ENABLED 全量 + 内存建树 + 禁用父整枝剪枝 + sort（verifies: TC-001）
- [ ] 任务 2 — brands：启用过滤、keyword LIKE（参数化+转义）、size 收敛 200、id 字符串（verifies: TC-002）
- [ ] 任务 3 — 空树/空品牌返 []（verifies: TC-004）
- [ ] 任务 4 — 网关两路径匿名 permitAll；internal denyAll 回归（verifies: TC-003）

## Acceptance Criteria

- [ ] AC-003 — 匿名取树 200；禁用父节点整枝（含启用子节点）不出现；按 sort。
- [ ] AC-004 — brands 仅启用；keyword 模糊；size>200 收敛；空结果 []。
- [ ] AC-005 — 匿名 200；外网 /api/internal/** 仍 404。

## 执行顺序（Execution Order）

1. 任务 1/2 并行 → 3 → 4。

## 并行度（Parallelization）

任务 1 与任务 2 并行（不同 AppService）。

## Verification

- Unit: 建树剪枝夹具（禁用父+启用子）；keyword 转义。
- Integration: @SpringBootTest 树/品牌结果与顺序。
- API: 经 8080 匿名 200、internal 404。
- Migration: N/A。
- Error Case: 空态 []；恶意 keyword 通配被转义。
