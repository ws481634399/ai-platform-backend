# DU Implementation — DU-BE-501

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

M3 前置五项就绪修复，全部落在 repo-1，按任务组织：

1. **字段级字符串 ID 序列化（mall-common-web）**：新增元注解 `@StringId`
   （`@JacksonAnnotationsInside` + `@JsonSerialize(ToStringSerializer)`），仅作用于出参；
   Jackson 原生传播到 record 组件/访问器。入参 ID 保持 `Long/long`（Jackson 原生接受 `"123"`）。
2. **服务间共享凭证（mall-common-security）**：`InternalSecurityProperties`
   （`mall.security.internal.shared-secret` / `path` 默认 `/api/internal/**`）+ `InternalIdentityFilter`
   （OncePerRequestFilter：无 `X-Internal-Token` / 密钥不匹配 → 401 `INTERNAL_UNAUTHORIZED` 同构 JSON 中断，
   **即使携带合法 JWT 也不放行**；匹配则注入 `ROLE_SERVICE`；`MessageDigest.isEqual` 常量时间比较；
   finally 清理 SecurityContext）。`InternalSecurityAutoConfiguration` 经
   `@ConditionalOnProperty(prefix="mall.security.internal", name="shared-secret")` 自动装配，并关闭
   FilterRegistrationBean 向 servlet 容器的重复注册。
3. **网关白名单显式化 + internal 外拒（mall-gateway）**：白名单收敛为本 Change 最小集
   （`/api/admin/auth/login`、`/api/admin/auth/refresh`、`/api/mall/products/**`、`/actuator/health`）；
   `/api/internal/**` 置于 permitAll 之后 denyAll；authenticationEntryPoint（匿名）与 accessDeniedHandler
   （持权）在内部路径上统一改写为 **404 同构体**（`B0001` / "not found"），不暴露内部端点存在性；
   其余路径维持 401/403。新增真实安全链切片测试 `CHG0015GatewaySecurityChainTest`（7 例）。
4. **product 字符串 ID + 真实价区 + 有效 SKU 可见性**：mall/admin/internal 全部业务 ID 出参标注
   `@StringId`（金额、数量、level/sort 等保持 number；Request 入参不加）；admin 创建类接口回显由
   `Map.of("id", id)` 改为 `IdView`；价区新增 `PriceRangePo` + SkuMapper 单条分组 SQL
   `MIN/MAX(sale_price) ... WHERE status='ENABLED' AND deleted=0 AND product_id IN (...) GROUP BY product_id`，
   `ProductRepositoryImpl.mallPage` 分页后一次批量填充（杜绝 N+1），并加 `EXISTS(启用 SKU)` 过滤；
   详情对「无启用 SKU」返回 404；internal 接入共享凭证过滤器，yml 增加密钥占位。
5. **inventory 分页/ID/凭证/403**：补 `mybatis-plus-jsqlparser` 依赖与 `MybatisPlusConfig`
   （分页 count 恢复）；DTO 业务 ID 标注 `@StringId`；`SkuClient` 携带 `X-Internal-Token` 默认头；
   安全链接入内部过滤器；yml 增加密钥占位。

详细文件清单见 evidence/changeset.md，红/绿与回归证据见 evidence/red-green.md。

## Commits

| Commit | 任务 | 说明 |
|---|---|---|
| 335c47f7 | 1 | feat(common-web): @StringId |
| 6c3d5835 | 2 | feat(common-security): InternalIdentityFilter 共享凭证 |
| 8c500f16 | 4 | feat(product): 字符串 ID + 真实价区 + EXISTS + 内部凭证 |
| 78ed07be | 5 | fix(inventory): 分页 + 字符串 ID + 凭证 + 403 |
| e050ec92 | 3 | feat(gateway): 显式白名单 + internal 404 外拒 |

完整短 hash/消息对照见 evidence/commits.md。

## Deviations

### DEV-1 内部密钥缺省策略：fail-fast → 条件装配
- 原 DU 建议: task-design.md §3「InternalSecurityProperties（mall.security.internal.*，非 dev 缺密钥 fail-fast）」。
- 实际实现: 采用 `@ConditionalOnProperty(prefix="mall.security.internal", name="shared-secret")`，
  未配置密钥时自动配置整体不生效（过滤器不注册）；各服务 application.yml 统一给
  `${MALL_INTERNAL_SHARED_SECRET:dev-internal-secret}` 占位。
- 原因: starter 模块无法可靠感知运行方 profile，硬编码 dev 判定会把环境约定写进公共件；安全默认应是
  「无配置 = 没有 SERVICE 身份来源」，配合各服务安全配置中 internal 路径 `hasRole("SERVICE")`，
  缺密钥时内部接口只会更严格地全部拒绝，不会误放行。
- 影响评估: 生产经环境变量注入密钥；测试由自动装配默认值生效并被 InternalIdentityFilterTest/
  Internal*ApiTest 锁定。无放松面。

### DEV-2 分页插件方言：显式 MYSQL → 无参自动探测
- 原 DU 建议: task-design.md §3/§6「MybatisPlusConfig（PaginationInnerInterceptor MYSQL）」。
- 实际实现: `new PaginationInnerInterceptor()` 无参构造，由 MyBatis-Plus 按数据源自动探测方言。
- 原因: 与 product 模块既有配置保持一致；测试跑 H2（MySQL 兼容模式）、生产连 MySQL，自动探测两种环境均正确，
  避免在基础设施代码硬编码数据库类型。
- 影响评估: InventoryAdminApiTest 以 33 行/每页 10 条、total 恒定 33、末页 3 条锁定分页 count 行为。

### DEV-3 附带修复两个被测试暴露的潜伏生产缺陷（task-design 未列出）
- 原 DU 建议: task-design.md 仅要求新增分页配置与 DTO/凭证改造。
- 实际实现:
  1. `InventoryAdminController` 全部 `@RequestParam`/`@PathVariable` 补显式名称——无 `-parameters`
     编译时原写法会导致参数名丢失、请求 400（`parameter name information not available`）；
  2. 新增 `InventorySecurityExceptionAdvice`，将方法级 `@PreAuthorize` 抛出的
     `AccessDeniedException`（发生在过滤链之后）映射为 403——原先被全局
     `@ExceptionHandler(Exception.class)` 吞成 S0001/500，与 product 模块既有处理对齐。
- 原因: 两项均为本 DU 集成测试真实暴露的库存模块存量缺陷，不属于设计外功能扩张，就地修复才能让
  TC-005（库存分页）与内部凭证用例获得正确断言基线。
- 影响评估: 仅修正错误状态码（400→正常绑定、500→403），不改变任何业务语义；product 模块同模式已在 M2 验证。

## 自检

- [x] 全量构建：`mvn clean package` → 24 模块 BUILD SUCCESS，13 个测试模块 **197/0/0/0**
      （通过/失败/错误/跳过），日志 evidence/logs/backend-full-package-run1.log。
- [x] 关键单测：product 70/70、inventory 19/19、gateway 13/13（新增 7）、
      InternalIdentityFilterTest 8 例、StringIdJacksonTest 通过。
- [x] 序列化边界：仅业务 ID 出参变字符串；金额（分）、数量、分页/排序字段保持 number；入参 ID 保持 Long。
- [x] 内部契约：无凭证/错凭证 401，合法 JWT 无内部凭证同样 401；服务间直连经 X-Internal-Token 放行；
      经网关访问 `/api/internal/**` 匿名与持 ADMIN Token 均为 404 同构体。
- [x] 价区口径：`MIN/MAX(product_sku.sale_price) WHERE status='ENABLED' AND deleted=0`，一条分组 SQL；
      无启用 SKU 商品商城列表不可见（EXISTS）、详情 404。
- [x] 范围克制：网关白名单仅本 Change 最小集，未提前纳入 home/categories/brands/skus（属后续 Change）。
- [x] 未引入计划外依赖（mybatis-plus-jsqlparser 为分页插件 3.5.9+ 必需，见 DEV-2/DEV-3 背景）。
