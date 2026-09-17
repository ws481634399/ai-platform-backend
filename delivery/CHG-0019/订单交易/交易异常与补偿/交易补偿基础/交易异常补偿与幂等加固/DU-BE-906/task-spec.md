# DU Task Spec — DU-BE-906

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-906 引用该表，不新造 DU。

## 0. 元信息

- DU id: DU-BE-906
- Change ID: CHG-0019
- Feature Path: 订单交易/交易异常与补偿/交易补偿基础/交易异常补偿与幂等加固
- 权威来源: story-design.md §5 / DU-BE-906

## 任务清单

- [ ] 任务 1 — V2 Flyway compensation_task（uk_business_op/idx_status_next，H2 兼容）（verifies: TC-002）
- [ ] 任务 2 — CompensationTask 聚合/PO/Mapper/Repository（insertIgnoreOrGet、findDue(limit)、条件更新、page/findById、payload JSON 合并）（verifies: TC-005, TC-008, TC-010）
- [ ] 任务 3 — InventoryCompensationHandler（release/confirm；目标态判成功幂等；故障脚本可测）（verifies: TC-004）
- [ ] 任务 4 — CompensationService：compensateOnceOrRecord（同步一次→失败落任务）、runDueTask（退避/DEAD）、manualRetry、page（verifies: TC-001~007）
- [ ] 任务 5 — 业务接线替换：OrderCreateService/PaymentService/OrderCancelService 三处 ERROR 挂点改补偿服务（verifies: TC-001~003）
- [ ] 任务 6 — CompensationRetryScheduler（@Scheduled 30s、LIMIT 50、单条隔离、@ConditionalOnProperty 开关）（verifies: TC-010, TC-011）
- [ ] 任务 7 — AdminCompensationController（GET 列表/POST retry，order:compensation 权限码）（verifies: TC-006, TC-007）
- [ ] 任务 8 — 审计日志（orderNo/traceId/operation/retryCount；无 secret）（verifies: TC-009）
- [ ] 任务 9 — @EnableScheduling 与配置项（scheduler-enabled/scan-delay-ms/scan-limit）（verifies: TC-011）

## Acceptance Criteria

- [ ] AC-001 — 锁后单失败：同步 release 成功无任务；release 也失败落 PENDING，重试至 SUCCESS。
- [ ] AC-002 — cancel/pay 库存副作用首次失败落 PENDING，退避重试成功。
- [ ] AC-003 — 重复 pay/cancel 不重复副作用；handler 对目标态 reservation 直接判成功。
- [ ] AC-004 — 5 次失败 FAILED_DEAD + ERROR；admin 可查；人工 retry 重置执行可成功。
- [ ] AC-005 — 同业务键并发只产生一行任务（唯一键复用）。
- [ ] AC-006 — 失败日志含 orderNo/traceId 可定位，无 secret/token；迁移仍有 history。
- [ ] AC-007 — 拾取有界 LIMIT、单条异常不影响后续、无无限重试、调度可配置关停。

## 执行顺序（Execution Order）

1. 任务 1 → 2 → 3 → 4 → 5 → 6/9 → 7 → 8。

## 并行度（Parallelization）

任务 7（admin 端点）可与 5/6 并行。

## Verification

- Unit: 退避序列/边界/manualReset/payload 合并/单条隔离。
- Integration: V2 H2；可编排故障 mock 跑场景 A/B/C/D 链路；唯一键并发复用；调度走 service 直调（不依赖真实定时）；双 context 验开关。
- API: admin GET/POST 200/400/404/403。
- Migration: V2 H2 前向执行。
- Error Case: 持续故障 FAILED_DEAD；日志捕获断言敏感字段缺席。
