# DU Task Spec — DU-BE-604

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 0. 元信息

- DU id: DU-BE-604
- Change ID: CHG-0016
- Feature Path: 商城前台/商城会员/收货地址/收货地址管理
- 权威来源: story-design.md §5 / DU-BE-604

## 任务清单

- [ ] 任务 1 — V2 shipping_address（含 default_member_flag 生成列与 uk）迁移（verifies: TC-010）
- [ ] 任务 2 — 聚合/工厂字段校验 + create（首条默认、20 上限 409）（verifies: TC-001, TC-008, TC-009）
- [ ] 任务 3 — list 排序 + update/delete 归属隔离（零行 404）（verifies: TC-002, TC-003, TC-004）
- [ ] 任务 4 — setDefault 同事务复位 + uk 并发 409；getDefault 空返 {item:null}；删除默认行为（verifies: TC-005, TC-006, TC-007）

## Acceptance Criteria

- [ ] AC-019 — 合法新增 201；首条地址 isDefault=true。
- [ ] AC-020 — 列表仅本人，is_default DESC、updated_at DESC。
- [ ] AC-021 — 改/删他人地址 404 且对方数据不变；删除自己地址 204。
- [ ] AC-022 — 设默认全表仅一条；并发其一 409，最终唯一。
- [ ] AC-023 — 删除默认地址后 getDefault 返回 {item:null}。
- [ ] AC-024 — 手机号/必填/detail 超长/邮编 6 位校验 400；第 21 条 409 ADDRESS_LIMIT。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3 → 4。

## 并行度（Parallelization）

无（同一聚合顺序实现）。

## Verification

- Unit: 工厂校验矩阵；首条默认/上限判定。
- Integration: 双会员越权矩阵；CountDownLatch 并发设默认；直接查库断言默认唯一。
- API: 201/204/400/404/409 契约与排序。
- Migration: 干净库 V2，生成列与 uk_default_member 存在。
- Error Case: 越权零行不区分 404；并发冲突回滚无半态。
