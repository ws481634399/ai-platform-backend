# DU Task Design — DU-BE-802

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

读车聚合：一次商品批量 + 一次库存批量（≤100，无 N+1）装配购物车读模型；条目双状态（itemStatus/stockStatus）、调价识别、条目级降级、选中合计；校验只读不改写 Redis。

## 2. Repository

repo-1（mall-cart 8104；调用 mall-product 8103 / mall-inventory 8106）

## 3. Scope

- CartViewController + GET /api/mall/cart 读模型装配：items[]{skuId, name, imageUrl, skuName, specs, priceFen(最新), priceFenAtAdded, quantity, selected, itemStatus, stockStatus}；selectedTotalFen、selectedCount。
- product 调用：DU-BE-801 的 sku/batch（商品状态/快照/最新价）。
- inventory 调用：CHG-0017 DU-BE-705 internal availability（精确数 → cart 内部映射阈值 10；注意 cart 拿到的是内部精确数）。
- itemStatus：VALID / PRODUCT_OFF_SHELF / SKU_INVALID / NOT_FOUND / PRICE_CHANGED（可与 VALID 并存：VALID+PRICE_CHANGED 标记，用数组或主状态+flags，契约按 story-design §2）；stockStatus：IN_STOCK/LOW_STOCK/OUT_OF_STOCK/UNKNOWN。
- 降级：product 5xx/超时 → 全部条目 itemStatus=UNKNOWN，HTTP 200；inventory 5xx → 仅 stockStatus=UNKNOWN。
- 合计：selectedTotalFen 仅累加 VALID+selected+非缺货（stockStatus≠OUT_OF_STOCK/UNKNOWN）条目，用最新 priceFen；整数分；selectedCount 按选中条目。
- 只读：装配过程不 HSET/EXPIRE Redis（TC-008 断言快照字节/TTL 不变）。

## 4. Design References

- CHG-0018 requirement-design.md §2.2（读模型/双状态/降级/金额口径）、§4（GET /cart 契约）；STORY-003-03-01-02 story-design.md §1/§2/§4；阈值 SSOT 在 CHG-0017（常量 10）。

## 5. Dependencies

权威表：无。实际依赖 DU-BE-801（Hash 结构/sku batch）、DU-BE-705（inventory availability）、DU-BE-501。

## 6. Implementation Sketch

- HGETALL 车 → skuIds（≤100）→ 并行两次内部批量（product batch、inventory availability），各一次。
- 以 product 快照为基准组装；缺失商品/快照 → NOT_FOUND；product.status≠ON_SALE → PRODUCT_OFF_SHELF；sku.status≠ENABLED → SKU_INVALID；价格比对：snapshot.priceFen != entry.priceFenAtAdded → PRICE_CHANGED 标记，展示价用最新值；请求体即使传 price 一律忽略。
- 库存映射 0/1-9/≥10 三态；itemStatus≠VALID 的条目不参与合计；UNKNOWN 库存条目不参与合计但仍展示。
- 边界审计：mall-cart 无 product/inventory 的 JDBC/数据源依赖（仅 RestClient）。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。读模型为双调用+字段映射直线装配；状态为常量判定。
