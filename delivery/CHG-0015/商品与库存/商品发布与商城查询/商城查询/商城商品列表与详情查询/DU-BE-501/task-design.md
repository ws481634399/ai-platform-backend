# DU Task Design — DU-BE-501

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-501 引用该表，不新造 DU。

## 1. Goal

M3 前置五项就绪修复：字段级字符串 ID 序列化、网关白名单与 internal 外拒、服务间共享凭证、库存分页修复、列表价区与有效 SKU 过滤。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-common-web：`annotation/StringId.java`（元注解：@JacksonAnnotationsInside + @JsonSerialize(ToStringSerializer)）。
- mall-common-security：`InternalIdentityFilter`（OncePerRequestFilter，校验 X-Internal-Token，注入 ROLE_SERVICE）、`InternalSecurityProperties`（mall.security.internal.*，非 dev 缺密钥 fail-fast）。
- mall-gateway：GatewaySecurityConfiguration 白名单显式枚举 + `/api/internal/**` denyAll（404 同构错误体）。
- mall-product：mall/admin DTO 业务 ID 字段标注 @StringId（金额/分页不动）；SkuMapper 价区分组查询；ProductRepositoryImpl.mallPage 增 EXISTS 启用 SKU 与价区填充；internal 接入过滤器；yml 密钥占位。
- mall-inventory：新增 MybatisPlusConfig（PaginationInnerInterceptor MYSQL）；DTO @StringId；InternalIdentityFilter 注册；SkuClient 加默认头。
- 各服务 application.yml：`mall.security.internal.shared-secret: ${MALL_INTERNAL_SHARED_SECRET:dev-internal-secret}`。

## 4. Design References

- CHG-0015 requirement-design.md §2（五项方案）、§4（内部头契约）、§5.1（@StringId/价区 SSOT）。
- CHG-0015 story-design.md §1（模块改动）、§2（契约变化）、§4（错误处理）。

## 5. Dependencies

无（本 DU 为 M3 基线，后续 Change 依赖它）。

## 6. Implementation Sketch

- 序列化：`@StringId` 标于 record 组件（Jackson 原生传播到构造参数/访问器）；入参 ID 保持 Long/long（Spring/Jackson 原生接受 "123"）。审计范围 `interfaces/rest/**/dto`。
- 请求过滤链：internal servlet 过滤器仅匹配 `/api/internal/**`；头不匹配即写 UnifyResult 401 并中断；匹配则设置 Authentication(principal=AuthenticatedSubject("SERVICE",SERVICE))。
- 网关链：exchange 匹配顺序 = 白名单 permitAll → internal denyAll → admin hasRole ADMIN → any authenticated。
- 价区查询流：mallPage(ON_SALE+EXISTS enabled sku) → 收集 productIds → 一次 `SELECT product_id, MIN(sale_price), MAX(sale_price) FROM product_sku WHERE status='ENABLED' AND product_id IN (...) GROUP BY product_id` → 内存 map 填充 ListItem；无启用 SKU 商品被 SQL 谓词排除。
- 分页：InventoryMybatisPlusConfig 注册 MybatisPlusInterceptor；Page 查询自动 count；修复后回归 33 行 total。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。理由：五项均为配置/注解/单条 SQL 与过滤器直线逻辑，无算法/状态机；关键控制流已在 §6 表达。
