# DU Implementation — DU-BE-802

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

mall-cart 读车实时聚合（仅 mall-cart 一个模块，mall-product/mall-inventory/mall-gateway 零改动，
复用 DU-BE-801 的 sku/batch 与 CHG-0017 DU-BE-705 的 availability 既有契约）：

- 出站端口与适配：
  - `application/cart/InventoryAvailabilityClient.java`（端口 + SkuAvailability 精确数量 record，刻意无 lock 语义）；
  - `infrastructure/client/RestInventoryAvailabilityClient.java`：RestClient POST
    `http://${mall.cart.inventory-uri:8106}/api/internal/inventory/availability`，X-Internal-Token；
    传输/信封故障统一 503 S0301 由读模型降级；@StringId 字符串 ID parseLong 兼容（同 801 模式）。
- 读模型：
  - `application/cart/CartReadModel.java`：CartLine/CartView record + ItemStatus
    （VALID/PRODUCT_OFF_SHELF/SKU_INVALID/NOT_FOUND/PRICE_CHANGED/UNKNOWN）与
    StockStatus（IN_STOCK/LOW_STOCK/OUT_OF_STOCK/UNKNOWN）枚举；
  - `application/cart/CartViewAssembler.java`：纯函数直线装配。状态优先级——product 故障 UNKNOWN >
    快照缺失 NOT_FOUND > 商品非 ON_SALE PRODUCT_OFF_SHELF > SKU 非 ENABLED SKU_INVALID >
    最新价≠快照价 PRICE_CHANGED > VALID；库存 0/1..9/≥10 三态（阈值常量
    `CartConstants.IN_STOCK_THRESHOLD=10`，注释标注与 CHG-0017 同 SSOT）；
    合计仅 VALID+selected+IN_STOCK/LOW_STOCK，按 product 最新价 ×quantity 计整数分；
  - `application/cart/CartQueryService.java`：HGETALL → 空车零依赖调用短路；否则 product/inventory
    各一次批量（无 N+1，100 条目上限即单批）；两边 RuntimeException 分别降级（product 全条目 UNKNOWN、
    inventory 仅库存 UNKNOWN），仅 Redis 故障归一 503。
- 接口：GET /api/mall/cart 替换为 CartReadView（items[]{skuId,quantity,selected,productId,productName,
  skuName,specs,imageUrl,priceFen,priceFenAtAdded,itemStatus,stockStatus,createdAt,updatedAt}
  +selectedTotalFen+selectedCount），恒 200 含降级条目；写操作响应保持 801 原始 CartView 不变
  （前端写后强拉 GET）。雪花 ID 字符串、priceFen 可空。
- 配置：application.yml 增 `mall.cart.inventory-uri`（env INVENTORY_SERVICE_URI，默认 8106）；
  CartConstants 增 IN_STOCK_THRESHOLD。
- 测试（新增 18 例，mall-cart 23→41 全绿）：
  - CartViewAssemblerTest 10 例（纯表驱动：状态矩阵/调价/0/1/9/10 阈值边界/缺库存记录/双降级/合计排除）；
  - CartReadApiTest 6 例（真实 Redis+MockMvc+双 mock：回填字段/七条目矩阵合计/双依赖降级/空车零调用/
    读不改 Redis——Hash 内容与 TTL 双断言）；
  - CartBoundaryAuditTest 2 例（AC-021：pom 无 JDBC/MyBatis/JPA/Flyway/驱动；主干无 java.sql/
    jakarta.persistence/mybatis/spring-jdbc import）；
  - CartApiTest 既有 13 例补 inventory mock 夹具，全部不回归。

## Commits

| Commit | 类型 | 说明 |
| --- | --- | --- |
| 2ae0dabc… | baseline | DU-BE-801 evidence/日志 docs 收尾提交（本 DU 起始基线，完整 40 位见 metadata.yaml） |
| `34a0eb2c59b93a6f38db65f4c9ba172c0cd3b97d` | feat(cart) | DU-BE-802 读车实时聚合：inventory 端口+Rest 适配、CartReadModel/Assembler/QueryService、GET 读模型替换、边界审计（13 文件，测试 41/41） |
| （docs 提交） | docs(sdd) | DU-BE-802 implementation/metadata/evidence 回填，非代码提交 |

## Deviations

### DEV-1
- 原 DU 建议: task-design §3 行字段含 skuName；story-design §2 CartLine 含 productName/skuName/specs。
- 实际实现: product sku/batch 契约无独立 SKU 名称字段（SkuBatchItem 仅 skuCode），CartLine.skuName 承载 skuCode。
- 原因: 不为本 DU 扩 product 既有契约（无数据模型字段可映射，扩契约会越界改动 mall-product）。
- 影响评估: 前端行内规格仍由 specs 名值对完整展示，skuName 显示 SKU 编码，与详情页 skuCode 口径一致；DU-FE-801 按此对接。

### DEV-2
- 原 DU 建议: story-design §1.6「PRICE_CHANGED 叠加在 VALID 上」的措辞与单字段 itemStatus 契约存在张力。
- 实际实现: itemStatus 单字段（严格按 story-design §2 CartLine 契约，不新增 flags 字段），调价行取 PRICE_CHANGED；
  selectedTotalFen 严格按 AC-013「仅 VALID+选中」实现，PRICE_CHANGED 行不参与合计（其最新价仍在 priceFen 展示）。
- 原因: AC-013 是可验收硬口径；调价需用户确认新价后再纳入合计更安全，M4 结算价本就独立重算。
- 影响评估: 调价商品在前端表现为「不计入合计+新价提示」；DU-FE-801 购物车页据此渲染，无金额信任风险。

## 自检

- mall-cart `mvn test` **41/41**（0 failures/0 errors/0 skipped）；`mvn package -DskipTests` BUILD SUCCESS。
- 真实环境（identity 8101/product 8103/cart 8104/inventory 8106/gateway 8080 真实 jar + Docker infra）：
  会员登录经网关 GET 返回真实商品名/规格/图/最新价（399900）、VALID+IN_STOCK、selectedTotalFen=399900；
  停 inventory → 整车 200、stockStatus=UNKNOWN、itemStatus 仍 VALID、合计 0、数量/选择照常展示；
  inventory 重启后自动恢复 IN_STOCK/合计恢复（未重新部署 cart，降级为运行时行为）。
- 只读复核：自动化断言读车前后 Hash 字节一致且 TTL 不续期；真实 E2E 期间加购 1 件外无写操作。
- 边界审计：AC-021 双自动断言（pom+源码 import）通过；cart 对 product/inventory 仅两条 RestClient 出站路径。
