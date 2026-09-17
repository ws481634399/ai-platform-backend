# DU Task Spec — DU-BE-902

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-902 引用该表，不新造 DU。

## 0. 元信息

- DU id: DU-BE-902
- Change ID: CHG-0019
- Feature Path: 订单交易/订单预览与创建/订单预览与创建/订单创建与库存锁定
- 权威来源: story-design.md §5 / DU-BE-902

## 任务清单

- [ ] 任务 1 — V1 Flyway：orders/order_item/order_status_history 三表 + 全部索引（verifies: TC-001）
- [ ] 任务 2 — Order/OrderItem/ReceiverSnapshot/OrderStatusHistory 聚合与 OrderStatus/OrderOperation 迁移表（verifies: TC-012）
- [ ] 任务 3 — SnowflakeOrderNoGenerator（ORD+时间+4位序列，uk 冲突重试 3 次）（verifies: TC-011）
- [ ] 任务 4 — PO/Mapper/MyBatisOrderRepository：insertOrderTx 三表同事务、findBySubmitToken、casStatus 预留（verifies: TC-001, TC-006）
- [ ] 任务 5 — OrderCreateService：token 消费/指纹校验/二次重查/金额重算/逐行 lock（verifies: TC-001~004, TC-007, TC-008）
- [ ] 任务 6 — 失败补偿（本 DU 版）：部分锁失败与落库失败 best-effort release + ERROR 日志（verifies: TC-009, TC-010）
- [ ] 任务 7 — 双层幂等：GETDEL + uk_member_submit_token DuplicateKey 回放首单（verifies: TC-005, TC-006）
- [ ] 任务 8 — CART 成功后 cart batch-delete（BUY_NOW 不调）（verifies: TC-003）
- [ ] 任务 9 — POST /api/mall/orders + OrderDetailView assembler（verifies: TC-001, TC-004）
- [ ] 任务 10 — 网关 mall-order-mall/admin 路由 + 安全 MEMBER 放行（verifies: TC-015）

## Acceptance Criteria

- [ ] AC-001 — 无/伪造/他人/重放 token → B0406；同 token 双提交仅一单、锁一次、同 orderNo。
- [ ] AC-002 — addressId/items/source 与令牌载荷不一致 → B0406。
- [ ] AC-003 — 请求携带 price/totalAmount/payAmount 全部忽略，按 product 重查价落库。
- [ ] AC-004 — 地址非本人/不存在 → 创建失败且无锁定。
- [ ] AC-005 — 库存充足：PENDING_PAYMENT 单、快照/金额/history(CREATE) 完整、reservation 全 LOCKED、orderNo 唯一且格式正确。
- [ ] AC-006 — 库存不足 → B0404，无订单、库存不为负、无悬挂 reservation。
- [ ] AC-007 — 第 N 行锁失败 → 前 N-1 行最终 RELEASED，整体失败无订单。
- [ ] AC-008 — 锁后落库失败 → 已锁同步 release，接口返回创建失败。
- [ ] AC-009 — CART 成功后 batch-delete 清已购项；BUY_NOW 不动车；失败车不变（前端行为在 DU-FE-901，本 DU 保证接口幂等可清）。
- [ ] AC-010 — 网关 MEMBER 可达、未认证 401、/api/internal 经网关不可达。

## 执行顺序（Execution Order）

1. 任务 1 → 2/3 → 4 → 5 → 6/7/8 → 9 → 10。

## 并行度（Parallelization）

任务 10（网关配置）可与 5–9 并行。

## Verification

- Unit: 聚合/orderNo 生成器单测；`mvn -pl mall-services/mall-order test`。
- Integration: H2 Flyway V1 + Redis Testcontainers；端口 mock 断言 lock/release/cart-delete 调用次数与参数；latch 并发双提交。
- API: 200/400/404/409/503 契约；DuplicateKey 回放 200 同 orderNo。
- Migration: V1 在 H2 MODE=MySQL 执行通过；启动 Flyway 校验。
- Error Case: 第 N 行锁不足、落库异常两类部分失败路径。
