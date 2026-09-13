# DU Implementation — DU-BE-302

## Status

completed

## Actual Implementation

分类管理后端（含 M2 公共前置：mall-product 接入资源服务器安全链、RBAC 权限/菜单种子、网关路由）。
严格 DDD 四层：domain（聚合 + 纯领域服务 + 仓储端口）→ application（事务编排）→ infrastructure（MyBatis-Plus 适配器/安全配置）→ interfaces（REST + DTO）。

主要文件：
- 领域层 `mall-services/mall-product/src/main/java/com/ai/mall/product/domain/`
  - `category/Category.java`：分类聚合（名称 trim/非空/≤32、启停状态、层级迁移）
  - `category/CategoryRules.java`：跨实体不变量纯静态服务（新建层级 1~3、移动自引用/祖先环/禁用父/子树深度校验）
  - `category/CategoryLevel.java`、`category/CategoryRepository.java`、`category/CategoryException.java`
  - `shared/MasterDataStatus.java`、`shared/ProductErrorCode.java`（B2101~B2107）
- 应用层 `application/category/CategoryApplicationService.java`、`CategoryCommands.java`
  - 移动时同事务重算子树 level；查重 + 唯一索引双保险（DuplicateKeyException → B2102）
- 基础设施层
  - `infrastructure/persistence/category/`：CategoryPo / CategoryMapper(BaseMapper) / CategoryRepositoryImpl（insert 回读审计时间）
  - `infrastructure/config/ProductSecurityConfiguration.java`：`@Profile("!test")` 资源服务器，JWT 验签 + issuer/audience 校验，401/403 JSON 统一响应
  - `db/migration/V1__create_product_category_and_brand.sql`：product_category、product_brand 两表（品牌表供 DU-BE-303 使用，随本 DU 公共前置落库）
- 接口层 `interfaces/rest/admin/`
  - `CategoryAdminController.java`：/api/admin/categories（tree/get/create/update/status），`@PreAuthorize hasAuthority('product:category:*')`
  - `dto/CategoryDtos.java`、`dto/CategoryTreeAssembler.java`（平铺→树，含禁用节点，sort/id 稳定排序）
  - `ProductSecurityExceptionAdvice.java`：方法级 AccessDeniedException → 403
- 公共前置
  - `mall-identity/.../db/migration/V3__add_product_category_brand_permissions.sql`：8 个权限码 + 3 个菜单（商品目录/分类页 CategoryTree/品牌页 BrandList）+ 超管幂等授权
  - `mall-gateway/src/main/resources/application.yml`：/api/admin/categories/**、/api/admin/brands/** 静态路由，默认直连 http://localhost:8103，可用 MALL_GATEWAY_PRODUCT_URI 覆写为 lb 寻址
  - 根 `pom.xml`：maven-compiler-plugin 开启 `-parameters`（Spring MVC 参数名反射）
  - mall-product `pom.xml`：引入 mall-common-security + oauth2-resource-server；application.yml 增加 mall.security.jwt.*

测试：
- `CategoryRulesTest`（7）：层级递进、禁用父、自引用、祖先环、合法移动层级重算、子树超限
- `CategoryTest`（3）：名称规范化、启停不级联语义、非法状态
- `CategoryAdminApiTest`（11，@SpringBootTest + H2/Flyway V1 + 临时 RSA TestSecurityConfig）：TC-001~TC-010、同级重名 409、详情 404、无权限 403/无 token 401

## DDD Conformance

- 依赖方向 interfaces → application → domain；infrastructure 实现 domain 端口。
- 层级/环/禁用父等不变量全部在 domain（Category 聚合 + CategoryRules 纯函数），应用服务只编排加载与持久化。
- 接口层 DTO record 不携带领域对象；树组装为 interfaces 侧纯装配器。

## Verification

- `mvn -pl mall-services/mall-product -am test`：22 passed（product）
- `mvn -pl mall-services/mall-identity -am test`：50 passed；Flyway V1/V2/V3 在 H2(MODE=MySQL) 成功迁移
- `mvn -pl mall-gateway -am test`：5 passed（路由 yml 绑定正常）
- 三模块联跑命令与完整输出见 evidence/test-output.log

## Deviations

- 原跨 Story 公共 DU-BE-301（权限种子/安全配置/网关路由独立成 DU）在 task 阶段被 harness 拒绝（非 story §5 DU 表成员不被识别），已并入本 DU 实施，文档与 design gate 已同步。
- gateway 未引入 spring-cloud-starter-loadbalancer（当前注册中心默认关闭），路由默认 URI 采用 http://localhost:8103 直连，保留 `MALL_GATEWAY_PRODUCT_URI=lb://mall-product` 的覆写口子供部署环境启用服务发现。
- 根 pom 增加 `<parameters>true</parameters>`：`@PathVariable long id` 未显式指定 value 时依赖参数名反射，属于 Spring Boot 生态标准编译选项，全仓一次性补齐。

## 交付后联调补全（2026-09-13）：跨服务权限传播

### 缺陷现象

真实集成环境（网关 → mall-product）登录后访问 `GET /api/admin/categories/tree` 返回 403 permission denied。
自动化测试（ApiTestSecurityConfig 直接授予权限）与 MockMvc 契约测试无法暴露，属 converge 时登记的"浏览器手工联调走查"遗留项（见 workspace 仓 CHG-0010 convergence.md §3）。

### 根因

- mall-identity 签发的 JWT 不含权限码（仅 sub/subject_type/username/auth_version）；
- 网关 IdentityPropagationFilter 只透传身份头，不透传权限；
- mall-product 的 JwtSubjectConverter 从 JWT `permissions` claim 取权限，该 claim 恒为空 → `@PreAuthorize` 必 403；
- 另发现 identity 旧进程未配置 REDIS_PASSWORD，授权快照写 Redis 被静默吞掉（AuthorizationQueryService 对缓存异常 fail-open），快照实际从未落库。

### 修复内容（跨 CHG-0007 公共组件，由本 Story 公共前置 DU-BE-302 首个消费并承接）

- `mall-common-security` 新增：
  - `AuthorizationKeys`：授权快照键规范单点定义（`authz:{adminId}:{permissionVersion}` 快照 + `authz:current:{adminId}` 当前版本指针）；
  - `AuthoritySnapshot`：资源服务侧快照反序列化记录（忽略 menus 等无关字段）；
  - `RedisSnapshotAuthorityConverter`：JWT 解析身份 + 经共享 Redis 指针/快照加载权限码；快照缺失 → 401，Redis 故障 fail-closed；
  - pom 增加 spring-boot-starter-data-redis、jackson-databind。
- `mall-identity`：RedisAuthorizationCache.put 同步写版本快照与当前版本指针（同 15min TTL）；application.yml 增加 spring.data.redis.*（host/port/password 经环境变量注入）。
- `mall-product`：ProductSecurityConfiguration 改用 RedisSnapshotAuthorityConverter；application.yml 增加 spring.data.redis.*。

### 验证（2026-09-13 真实环境）

登录 → bootstrap 后 Redis 存在 `authz:current:1` 与 `authz:1:2`；
经网关 `GET /api/admin/categories/tree` → 200（空树）、`GET /api/admin/brands?pageNo=1&pageSize=10` → 200（空分页）。

### 对后续 Story/Change 的约束

CHG-0011/0012/0013 等下游资源服务的资源服务器应统一改用 RedisSnapshotAuthorityConverter 并配置 spring.data.redis.*；mall-inventory 尚未接入（CHG-0013 范围内）。
