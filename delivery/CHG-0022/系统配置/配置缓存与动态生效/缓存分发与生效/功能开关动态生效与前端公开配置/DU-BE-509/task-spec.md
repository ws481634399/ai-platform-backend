# DU Task Spec — DU-BE-509

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-509
- Change ID: CHG-0022
- Feature Path: 系统配置/配置缓存与动态生效/缓存分发与生效/功能开关动态生效与前端公开配置
- 权威来源: story-design.md §5 / DU-BE-509

## 任务清单

- [ ] 任务 1 — public-features 端点（聚合缓存/只返 public 键 key+enabled）（verifies: TC-001）
- [ ] 任务 2 — 网关匿名白名单与 admin 未登录 401 验证（verifies: TC-008）
- [ ] 任务 3 — mall-search ensureEnabled 切点与 403/恢复 IT（verifies: TC-002）
- [ ] 任务 4 — mall-cart 游客写开关（会员写/读不受影响/恢复）（verifies: TC-004）
- [ ] 任务 5 — effectType 展示数据支持（参数响应含 effectType 字段）（verifies: TC-005）
- [ ] 任务 6 — 故障 fail-open：Redis+system 全失时默认放行（verifies: TC-006）
- [ ] 任务 7 — mall-search/mall-cart mvn test 全绿（verifies: TC-007）

## Acceptance Criteria

- [ ] AC-001 — 匿名 200 仅返回 publicFlag 键 {key,enabled}，非公开不出现。
- [ ] AC-002 — search.enabled=false（≤60s）搜索 403 B0606；重开恢复。
- [ ] AC-004 — guest-cart=false 游客写 403；会员写/读不受影响；重开恢复。
- [ ] AC-005 — effectType 标识有数据支撑；热生效 ≤60s。
- [ ] AC-006 — 缓存全失服务端按代码默认决策（fail-open）。
- [ ] AC-007 — 前后端测试全绿（本 DU 负责后端部分）。

## 执行顺序（Execution Order）

1. 任务 1/2 → 3/4 → 5/6 → 7。

## 并行度（Parallelization）

任务 3 与 4 可并行。

## Verification

- Unit: 切点切片（Gate 两态/游客判定矩阵）。
- Integration: 真实翻转开关 IT（TTL 注入缩短）：搜索 200→403→200；游客 403/会员 200/读 200。
- API: public-features 200 字段白名单；网关 401/200。
- Migration: N/A。
- Error Case: 配置服务全失 fail-open、B0606 统一错误结构。
