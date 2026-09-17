# DU Task Design — DU-BE-902

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

订单 V1 持久化模型与创建编排：三表落库、雪花 orderNo、submitToken 双层幂等、逐行库存锁定与部分失败回滚（本 DU 为 best-effort release + ERROR 日志，STORY-004-04-01-01 升级为补偿任务）、购物车清理、网关路由。

## 2. Repository

repo-1（mall-order 新增 V1/聚合/创建链路；mall-gateway 路由）

## 3. Scope

- V1__order_init.sql（H2 MODE=MySQL 兼容；datetime(6)；JSON 用 varchar/text）：
  - orders：id bigint PK 雪花、order_no varchar(32) uk、member_id bigint、status varchar(24)、source varchar(16)、goods/discount/freight/pay_amount bigint、receiver_name/phone/province/city/district/detail_address/postal_code、delivery_company/tracking_no/cancel_reason、submit_token、paid_at/cancelled_at/shipped_at/completed_at、version int default 0、created_at/updated_at；uk_member_submit_token(member_id,submit_token)、idx_member_created、idx_status_created。
  - order_item：id bigint PK、order_id/order_no、product_id/sku_id、product_name/sku_code、specifications_json text、main_image_url、unit_price_fen/quantity/subtotal_fen、created_at；idx_order_id。
  - order_status_history：id bigint PK、order_id/order_no、from_status/to_status/operation/operator/reason、occurred_at；idx_order_occurred。
- domain.order：Order 聚合（create 工厂产出 PENDING_PAYMENT+CREATE history；reconstitute；items 不可变快照列表；version 乐观锁字段）；OrderStatus 枚举 NEXT 迁移表（CREATE→PENDING；PAY→PAID；CANCEL→CANCELLED；SHIP PAID→SHIPPED；CONFIRM_RECEIPT SHIPPED→COMPLETED）；canTransit。
- infrastructure.id.SnowflakeOrderNoGenerator：ORD + yyyyMMddHHmmssSSS + 4 位（AtomicLong 0..9999 循环）；uk 冲突由 service 捕获重取，最多 3 轮。
- persistence：OrderPo/ItemPo/HistoryPo + Mapper；OrderMapper 自定义 `casStatus`（status+version 双条件更新，本 DU 随 Mapper 提交，DU-BE-903 起使用）；insertOrderTx @Transactional（三表插入，version=0）；findByOrderNo/findByOrderNoForMember/findByMemberSubmitToken。
- application.order.OrderCreateService（不加类级事务；落库走 repository 的事务方法）：consume token → 校验载荷等值（source/addressId/规范化 items 指纹）→ 二次端口重查（product/inventory/address）→ Money 重算 → orderNo → 逐行 lock（reservationId=`orderNo:skuId`）：任一行失败，已锁行逐个 release（best-effort，失败记 ERROR），抛 B0404/B0409 → insertOrderTx：捕获 DuplicateKeyException → findByMemberSubmitToken 回放；其他 RuntimeException → 全量 release + ERROR + 抛 500 → CART 成功后 cartPort.deleteSelected/批量删除已购 sku（失败仅 WARN，不影响成单结果——按 design：购物车清理失败不回滚订单，记录日志）。
- interfaces.rest.mall：CreateOrderRequest{submitToken,addressId,source,items?}；MemberOrderController POST /；OrderDetailAssembler（ID 字符串、Fen、history）。
- 网关：application.yml 增 mall-order-mall（/api/mall/orders/**→lb/直连 8105）、mall-order-admin（/api/admin/orders/**,/api/admin/compensations/**→8105）；GatewaySecurityConfiguration MEMBER 路径集追加 /api/mall/orders/**。

## 4. Design References

- requirement-design.md §2.2（数据模型）、§2.4（创建链路与补偿编排）、§4.1/§4.2（契约/错误码）；STORY-004-01-01-02 story-design.md §1–§4。

## 5. Dependencies

权威表：DU-BE-901。实际：inventory lock/release 内部端点（CHG-0016）、cart batch-delete 内部端点（CHG-0018，若缺失需在本 DU 于 mall-cart 补该内部端点）。

## 6. Implementation Sketch

- 指纹：preview 载荷 items 规范化为 `skuId:quantity` 排序拼接 JSON；create 请求体 items 同样规范化后字符串等值比较；CART 可只比较 source+addressId+cart 当前选中项重算指纹（实现以 preview 载荷存储的 items 为准，create 时 CART 请求忽略 body items，直接用载荷内 items）。
- 逐行锁循环：locked=List<reservationId>；lock 端口业务失败（不足）→ releaseAll(locked) best-effort → B0404；端口 5xx → releaseAll → B0409(503)。
- 落库事务边界：insertOrderTx 独立 bean 方法 @Transactional（PROPAGATION_REQUIRED）；库存调用全部在事务外，避免行锁持有期间做 HTTP。
- DuplicateKey 回放：SELECT by (member_id,submit_token) → assembler 200 返回；查不到（理论不可能）抛 500。
- release 失败处理本 DU 仅 ERROR 日志含 orderNo/reservationId/traceId；调用点以独立私有方法包裹，STORY-004-04-01-01 全局替换为 CompensationService。

## 7. Pseudocode

```
create(req, memberId):
  payload = tokenStore.consume(memberId, req.submitToken)   // null -> B0406
  if payload == null: throw ORDER_SUBMIT_TOKEN_INVALID
  assertFingerprint(payload, req)                            // 不等 -> B0406
  rows = resolveRows(payload)                                // CART 用 payload.items
  snapshots = productPort.batch(skuIds)                      // 5xx -> 503
  stock = inventoryPort.availability(skuIds)
  revalidate(snapshots, stock, addressPort.find(...))        // 不可售/不足/地址 -> 失败无锁
  money = Money.sum(snapshots, rows)
  for attempt in 1..3: orderNo = noGen.next() (uk 冲突重试)
  locked = []
  try:
    for r in rows:
      inventoryPort.lock(orderNo+":"+r.skuId, ...); locked.add(...)
  catch StockShort: releaseAllBestEffort(locked); throw B0404
  catch Dependency:  releaseAllBestEffort(locked); throw B0409
  try:
    order = Order.create(...); repo.insertOrderTx(order)
  catch DuplicateKey: return view(repo.findByMemberSubmitToken(...))
  catch Exception: releaseAllBestEffort(locked); log.error(traceId); throw 500
  if source==CART: try cartPort.deleteBought(memberId, skuIds) catch: log.warn
  return view(order)
```
