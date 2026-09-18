# DU Task Spec — DU-BE-507

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-507
- Change ID: CHG-0022
- Feature Path: 系统配置/功能开关与参数管理/配置管理与审计/配置模型、后台管理与变更审计
- 权威来源: story-design.md §5 / DU-BE-507

## 任务清单

- [ ] 任务 1 — mall-system 骨架（8108/公共组件接入/扫描路径）（verifies: TC-001）
- [ ] 任务 2 — V1 三表 DDL + 4 键种子（幂等）（verifies: TC-001）
- [ ] 任务 3 — feature_config 查询/启停/新建/删除服务与端点（UK B0602、404 B0603）（verifies: TC-002, TC-008）
- [ ] 任务 4 — system_parameter 类型/范围校验四反例（B0601 400 不变更）（verifies: TC-003）
- [ ] 任务 5 — version CAS（B0604/成功 +1）与内置保护（verifies: TC-004, TC-005）
- [ ] 任务 6 — system_config_history 同事务留痕与只读查询（verifies: TC-006）
- [ ] 任务 7 — identity V10 五权限码+3 菜单+授权；网关三路由；403 矩阵（verifies: TC-007）

## Acceptance Criteria

- [ ] AC-001 — V1 成功；种子存在 built_in=1/默认值范围正确；重复迁移不重复播种。
- [ ] AC-002 — 分页/分组/启停/新建/删除非内置可用；重复 key 409。
- [ ] AC-003 — 四类非法值 400 且库值不变。
- [ ] AC-004 — 旧 version 409；最新 version 成功。
- [ ] AC-005 — 内置删 key/改 key 拒绝、值可改；删非内置成功且留痕。
- [ ] AC-006 — 每次变更一条 history（old/new/operator/traceId/时间），可按 key 过滤分页，无写历史端点。
- [ ] AC-007 — 无 update 权限 403、可读；history 需 config-history:list；菜单按钮生效。

## 执行顺序（Execution Order）

1. 任务 1/2 → 3/4/5/6 → 7。

## 并行度（Parallelization）

任务 7（V10/网关）可与 3–6 并行。

## Verification

- Unit: 类型校验矩阵、CAS、内置保护、history 组装单测。
- Integration: testcontainers MySQL V1 迁移/种子幂等；服务级更新→history 联查。
- API: MockMvc 200/400/403/404/409 契约。
- Migration: V1 前向；V10 权限菜单断言。
- Error Case: UK 冲突、乐观锁冲突、越权、非法 JSON/越界。
