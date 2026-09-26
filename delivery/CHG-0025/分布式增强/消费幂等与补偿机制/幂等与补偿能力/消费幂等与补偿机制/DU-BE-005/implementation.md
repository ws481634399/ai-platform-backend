# DU Implementation — DU-BE-005

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

- mall-inventory V3 迁移：consumed_event 幂等表（event_id+consumer_group 唯一键，占位/回写/回滚对齐 mall-common-mq ConsumedEventRepository）。
- mall-order V6 迁移：同构 consumed_event（索引名 idx_consumed_aggregate，规避 H2 schema 级索引名冲突）。
- CompensationTask 扩展 OP_AUTO_CANCEL_ORDER 操作常量与 manualComplete 聚合行为（保留原 lastError 追加 MANUAL_COMPLETE）。
- 新增 CompensationActionHandler 接口化执行器：InventoryCompensationHandler 适配；新增 OrderAutoCancelCompensationHandler（反序列化 payload → OrderCancelService.systemCancel(COMPENSATION)）。
- CompensationService 列表化调度：ObjectProvider 延迟解析执行器打破构造器循环；执行期 MDC 沿用任务 traceId；新增 enqueueOrderAutoCancel；管理台 page(operation, aggregateId, status,...)。
- PaymentTimeoutCheckHandler：systemCancel 非 STATUS_CONFLICT 异常登记 ORDER_AUTO_CANCEL 补偿后重抛；CONFLICT 归并 SKIPPED。
- CompensationRepository page 扩为 6 参（businessType/operation/businessId/status），type/operation 精确 eq、businessId like、空串忽略。
- AdminCompensationController：operation 白名单 + aggregateId LIKE 通配符转义 + payload 透传 + POST /{id}/complete + compensation-audit 审计；权限码切 system:compensation:list/retry/complete。
- mall-identity V15：三权限种子、既有补偿菜单 permission_code 切换、SUPER_ADMIN 授权（旧 order:compensation 保留）。

## Commits

- "25f83ee"（T1）：mall-inventory V3 consumed_event
- "ce8d0e9"（T2）：mall-order V6 consumed_event
- "aa5be7f"（T3）：CompensationTask OP_AUTO_CANCEL_ORDER + manualComplete
- "0050c01"（T8）：CompensationRepository.page 过滤扩展（提前实施保证编译）
- "2bb9123"（T4-T5）：执行器接口化 + OrderAutoCancelCompensationHandler
- "e3bb8d2"（T6）：CompensationService 列表化调度/MDC traceId/自动取消登记
- "f9f2b81"（T7）：PaymentTimeoutCheckHandler 失败登记补偿后重抛
- "aba4dca"（T9）：补偿台操作/聚合筛选 + 人工完成 + 审计 + payload 透传
- "9c2849c"（T10）：V15 补偿管理三权限 + 菜单切换 + 超管授权
- "d30f9a7"（T11）：ObjectProvider 打破补偿循环 + 权限码切换测试对齐；全量回归

## Deviations

### DEV-1
- 原 DU 建议: mall-order consumed_event 索引名沿用 idx_aggregate。
- 实际实现: 索引名改为 idx_consumed_aggregate。
- 原因: H2（test profile）索引名为 schema 级唯一，order 库 V2 已存在 idx_aggregate，同名迁移失败；MySQL 为表级唯一不冲突。
- 影响评估: 仅索引名差异，查询语义/性能等价；MySQL 生产环境无影响。

### DEV-2
- 原 DU 建议: 补偿台查询路径与权限按全新模块设计（如 /api/admin/order-compensations + order:compensation 扩展）。
- 实际实现: 路径沿用 /api/admin/compensations；权限码切换为 system:compensation:list/retry/complete，旧 order:compensation 种子保留。
- 原因: 避免前端双入口与既有链接失效；菜单 path/component_key 不变，仅 permission_code 切换。
- 影响评估: 前端 DU-FE-003 只需增强既有页面；V15 保证超管三权限补齐。

### DEV-3
- 原 DU 建议: CompensationService 直接 List<CompensationActionHandler> 构造器注入。
- 实际实现: 改为 ObjectProvider 延迟解析（保留 List 构造器仅供单测）。
- 原因: handler → OrderCancelService → OrderCompensationPort(CompensationService) 构成构造器循环，Spring 3.x 默认禁止循环引用。
- 影响评估: 执行器首次执行时解析，语义不变；调度 30s 一次，解析开销可忽略。

## 自检

- mall-order：118/118 全绿（clean test；基线 105 + 本 DU 新增 13）。
- mall-inventory：37/37 全绿（clean test）。
- mall-identity：V15 在 InternalMemberSeedApiTest 上下文中迁移成功（版本 15 migration 日志确认）；该模块既有失败与本次无关。
