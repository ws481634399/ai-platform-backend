# DU Task Design — DU-BE-904

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

会员订单只读模型：分页列表（Tab/时间）、完整详情与状态历史，强制资源归属（越权 404），全部读库快照不调下游。

## 2. Repository

repo-1（mall-order）

## 3. Scope

- Repository 查询：`pageForMember(MemberOrderPageQuery)` MyBatis-Plus Page：WHERE member_id=? [AND status=?] [AND created_at>=? / <=?] ORDER BY created_at DESC,id DESC；`findDetailByOrderNoForMember(orderNo,memberId)`：orders 单条带 member_id 条件 → 不存在返回 empty（不区分原因）→ 批量取 order_item（按 id/行序）与 order_status_history（ORDER BY occurred_at ASC,id ASC）装配聚合。
- application.order.OrderQueryService：参数校验（OrderStatus 枚举 valueOf 容错 → 400；Instant.parse 失败 400；start>end 400；page>=1；size 1..100）；listForMember/getDetailForMember。
- interfaces.rest.mall：GET /api/mall/orders（@RequestParam 可空 status/startAt/endAt/page/size）；GET /api/mall/orders/{orderNo}。
- DTO record：PageView<T>、OrderSummaryView{orderNo,status,goodsAmountFen,payAmountFen,itemCount,firstItem,createdAt,keyTime}、FirstItemView、OrderDetailView（全字段：items/receiver/金额/物流/cancelReason/paidAt/cancelledAt/shippedAt/completedAt/history）、StatusHistoryView{fromStatus,toStatus,operation,operator,reason,occurredAt}、ReceiverView。
- ID 序列化为字符串（Jackson Long→String 按项目既有 @StringId/全局配置，实施核对）；金额 *Fen Long。

## 4. Design References

- requirement-design.md §4.4（REQ-M4-003 查询与履约规则）、§4 视图字段；STORY-004-03-01-01 story-design.md §1/§2。

## 5. Dependencies

权威表：DU-BE-902。查询只读 V1 表；无新增下游依赖。

## 6. Implementation Sketch

- 列表 firstItem：取该订单 order_item 最小 id 一行映射（图/名/skuCode/数量/单价）；itemCount=COUNT(items)（可在装配时一次 IN 查询分组，避免 N+1：page 内 orderIds 一次 select items + Java 分组）。
- 时间区间接受 ISO-8601（Instant）；Controller 层 String 接收，service 解析。
- 详情装配复用 Order 聚合 reconstitute 后经 assembler 输出，保证和创建/支付响应同构。
- 无 @Transactional 写操作；读方法可只读事务（可选）。

## 7. Pseudocode

N/A。查询为条件拼装 + 批量取数 + 内存分组的标准读模型，无复杂算法；IN 批量取数在 sketch 说明。
