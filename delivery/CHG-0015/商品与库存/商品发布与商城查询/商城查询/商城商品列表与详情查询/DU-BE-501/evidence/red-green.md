# Red-Green Evidence — DU-BE-501

> M3 前置五项修复：字段级字符串 ID 序列化、服务间共享凭证、网关白名单与 internal 外拒、库存分页修复、商城列表价区与有效 SKU 过滤。

## Red（改造前失败基线）

| 模块 | 证据日志 | 失败事实 |
|---|---|---|
| mall-product | logs/product-red-before.log | 70 例中 10 例失败：MallProductApiTest 5 例（价区 `expected: 2000L` 真实价区断言、字符串 ID 断言不过）；InternalProductApiTest 5 例（内部接口无凭证时返回 B0001/401 而非 INTERNAL_UNAUTHORIZED/404 契约） |
| mall-inventory | logs/inventory-green-run1.log | 分页插件类缺失编译失败（`PaginationInnerInterceptor` 位于独立的 mybatis-plus-jsqlparser 模块）；后续运行暴露库存列表无 -parameters 时 400、方法级鉴权拒绝落 500 两个潜伏缺陷 |
| mall-gateway | logs/gateway-green-run1.log | 新增安全链切片测试初次运行即红：`/api/internal/**` 外拒 404 契约尚无实现（测试先行） |

## Green（逐任务转绿）

| 模块 | 证据日志 | 结果 |
|---|---|---|
| mall-common-web | 全量构建日志（StringIdJacksonTest） | passed（@StringId 元注解序列化/入参不受影响） |
| mall-common-security | 全量构建日志（InternalIdentityFilterTest 8 例） | passed（无头/错头 401、合法 JWT 无凭证仍 401、正确凭证放行 ROLE_SERVICE、常量时间比较） |
| mall-product | logs/product-green-run2.log | 70/70 passed（价区单条分组 SQL、EXISTS 启用 SKU、字符串 ID、X-Internal-Token 5 例） |
| mall-inventory | logs/inventory-green-final.log | 19/19 passed（分页 33 行 total 回归、ID 字符串、凭证 4 例、403 映射） |
| mall-gateway | logs/gateway-green-final.log | 13/13 passed（新增 CHG0015GatewaySecurityChainTest 7 例：白名单放行、admin 401/200/403、internal 匿名与持 ADMIN Token 均 404 同构体） |

## Regression（全量回归）

| 证据日志 | 命令 | 结果 |
|---|---|---|
| logs/backend-full-package-run1.log | `mvn clean package`（24 模块，全部测试） | BUILD SUCCESS；13 个测试模块合计 **197/197 passed，0 failures，0 errors，0 skipped**；全部可执行模块 spring-boot repackage 成功 |
