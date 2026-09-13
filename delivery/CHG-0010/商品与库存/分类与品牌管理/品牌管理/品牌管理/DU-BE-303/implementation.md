# DU Implementation — DU-BE-303

## Status

completed

## Actual Implementation

品牌主数据后端能力（mall-product 单模块；安全链/权限码/网关路由复用 DU-BE-302）。

- 领域层 `domain/brand/`
  - `Brand.java`：聚合（名称 trim 非空 ≤64；logo 可空、非空须 http/https 且 ≤512；描述 ≤255；排序 0~9999；启停状态流转，禁用保留数据）
  - `BrandRepository.java`：端口 + BrandPageQuery/BrandPageResult 域内分页值对象（不依赖 MyBatis-Plus）
  - `BrandException.java`：B2121 不存在 404 / B2122 重名 409
- 应用层 `application/brand/`：BrandCommands、BrandApplicationService
  - 分页参数归一（page<1→1，size 越界→默认 20/上限 100）；同名应用层预判 + DuplicateKeyException 唯一索引兜底
- 基础设施层
  - `infrastructure/persistence/brand/`：BrandPo / BrandMapper / BrandRepositoryImpl（LambdaQuery 模糊/状态过滤、sort,id 排序、分页；existsByName 用 LOWER 跨库大小写不敏感）
  - `infrastructure/config/MybatisPlusConfig.java`：PaginationInnerInterceptor（方言自动探测，供后续商品模块复用）
  - mall-product pom 新增 `mybatis-plus-jsqlparser`（MyBatis-Plus 3.5.9 起分页插件 JSqlParser 独立模块）
- 接口层
  - `interfaces/rest/admin/BrandAdminController.java`：/api/admin/brands 五端点（分页/详情/创建/更新/启停），@PreAuthorize product:brand:* 细粒度权限
  - `dto/BrandDtos.java`：Create/Update/Status 请求校验 + BrandView + PageView&lt;T&gt;（records/total/page/size）
- product_brand 表已随 DU-BE-302 的 V1 迁移落库（uk_product_brand_name、idx_sort_id），本 DU 无新增迁移。

测试：
- `BrandTest`（5）：名称规范化/长度、logo 形态与长度、描述/排序、资料更新与启停、非法状态
- `BrandAdminApiTest`（9，@SpringBootTest + H2/Flyway + 分页插件 + 共享 ApiTestSecurityConfig）：TC-001~007 与 404，TC-009 双线程 CountDownLatch 并发同名恰一成功一 409、库中仅一条
- 原 CategoryAdminApiTest 的内嵌 TestSecurityConfig 提取为测试共享类 `support/ApiTestSecurityConfig`，分类/品牌两个集成测试复用。

## DDD Conformance

- interfaces → application → domain；infrastructure 实现 BrandRepository 端口，domain 不感知 MyBatis-Plus（分页为域内 record）。
- 名称/Logo/描述/排序不变量内聚于 Brand 聚合；唯一性由应用层预判 + 唯一索引双层保证。

## Verification

- `mvn -pl mall-services/mall-product -am test`：36 passed（含品牌 14、分类 21、冒烟 1）
- 完整输出见 evidence/test-output.log

## Deviations

- task-design 预期"大小写不敏感依赖 MySQL collation"，但 H2 2.x 默认字符串比较大小写敏感，无法仅靠 collation 对齐；existsByName 改为 `LOWER(name)=LOWER(?)` 参数化条件，跨库行为一致；生产侧唯一索引（utf8mb4_0900_ai_ci）继续承担并发兜底，TC-009 以完全同名验证唯一索引。
- MyBatis-Plus 3.5.12 的 PaginationInnerInterceptor 已从 extension 拆至 mybatis-plus-jsqlparser 独立模块，pom 显式补依赖（版本由 mybatis-plus-bom 管理）。
- 测试安全链从 CategoryAdminApiTest 抽取为共享 @TestConfiguration（support/ApiTestSecurityConfig），消除两份内嵌配置重复。
