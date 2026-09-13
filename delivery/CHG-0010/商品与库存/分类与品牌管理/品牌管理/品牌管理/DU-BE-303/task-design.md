# DU Task Design — DU-BE-303

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 requirement-design.md §6（SSOT）；本文件只按 DU id DU-BE-303 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-303 与同目录 task-spec.md（"做什么/验收"）配对互链。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

实现品牌主数据后端能力：名称全局唯一（trim/大小写不敏感）、分页与关键字/状态查询、启停准入（requirement-design.md §6 / DU-BE-303 goal 原文）。

## 2. Repository

repo-1（ai-platform-backend；本 DU 触达 mall-product 单模块；安全配置、权限码、网关路由由 DU-BE-302 先行落地，直接复用）。

## 3. Scope

- `mall-product/src/main/resources/db/migration/V1__create_product_category_and_brand.sql`：追加 product_brand 段（uk_product_brand_name、idx_sort）。
- `infrastructure/persistence/brand/`：po/BrandPO、mapper/BrandMapper、BrandRepositoryImpl。
- `infrastructure/config/MybatisPlusConfig.java`：PaginationInnerInterceptor（本 DU 新增，供后续商品模块复用）。
- `domain/brand/`：Brand 聚合、BrandRepository 端口、BrandException。
- `application/brand/BrandApplicationService.java`。
- `interfaces/rest/admin/BrandAdminController.java` 与 dto/brand/*（含分页响应包装）。
- `src/test/java/.../brand/`：领域单测、API 集成测试、并发同名测试。

## 4. Design References

- requirement-design.md §2（product_brand 表结构与字段约束）、§4（路径 `/api/admin/brands`、权限码 product:brand:*、UnifyResult）、§6 / DU-BE-303 权威行。
- story-design.md §1.1、§2（接口契约与大小写不敏感策略）、§3（迁移）、§4（错误处理与分页归一）。
- DU-BE-302 已建立的 ProductSecurityConfiguration、分层骨架与错误处理模式。

## 5. Dependencies

仓内 story 依赖表为 —（无）；Change 级依赖 DU-BE-302（安全配置/权限码注册/网关路由必须已合入 repo-1，requirement-design §6 依赖图）。DU-FE-302 依赖本 DU。

## 6. Implementation Sketch

```text
BrandAdminController (/api/admin/brands, @PreAuthorize hasAuthority('product:brand:*'))
  → BrandApplicationService (@Transactional)
      → Brand 聚合（create/rename/updateProfile/disable/enable；trim 与非空/长度内聚）
      → BrandRepository(端口) → BrandRepositoryImpl → BrandMapper
           ├─ selectPage(Page, LambdaQuery: name like / status eq / orderBy sort,id)
           └─ existsByName(name, excludeId)
      → MybatisPlusConfig.PaginationInnerInterceptor（分页 SQL 改写）
  ← UnifyResult.ok(PageView) / BrandException → GlobalExceptionHandler
```

数据流：分页参数先归一（page<1→1，size>100→100）→ MyBatis-Plus IPage 查询 → records 转 BrandView 列表 + total/page/size 组成 PageView。错误处理：应用层 existsByName 预判 + 数据库 uk_product_brand_name 兜底，DuplicateKeyException 统一转 BRAND_NAME_DUPLICATED；logo/description 走 Bean Validation。

## 7. Pseudocode

创建品牌（并发安全）：

```text
createBrand(req):
  name = trim(req.name)                       // @NotBlank @Size(max=64)
  validateLogoShape(req.logo)                 // 可空；非空则长度 ≤512 且 URL 形态
  if repository.existsByName(name, excludeId=null):
      throw BRAND_NAME_DUPLICATED             // 应用层预判（MySQL 排序规则本身大小写不敏感）
  brand = Brand.create(name, req.logo, trimToNull(req.description), req.sort ?? 0, ENABLED)
  try: repository.insert(brand)
  catch DuplicateKeyException:                // 并发漏网：唯一索引兜底
      throw BRAND_NAME_DUPLICATED
  return brand.id
```

更新品牌：

```text
updateBrand(id, req):
  brand = repository.findById(id) else throw BRAND_NOT_FOUND
  newName = trim(req.name)
  if newName != brand.name and repository.existsByName(newName, excludeId=id):
      throw BRAND_NAME_DUPLICATED
  brand.updateProfile(newName, req.logo, trimToNull(req.description), req.sort ?? 0)
  try: repository.update(brand)
  catch DuplicateKeyException: throw BRAND_NAME_DUPLICATED
```

分页查询：

```text
pageBrands(query):
  page = max(1, query.page); size = min(100, max(1, query.size))
  wrapper = LambdaQueryWrapper
  if query.keyword:  wrapper.like(name, query.keyword)
  if query.status:   wrapper.eq(status, query.status)
  wrapper.orderByAsc(sort).orderByAsc(id)
  result = mapper.selectPage(new Page<>(page, size), wrapper)
  return PageView(records=toViews(result.records), total=result.total, page=page, size=size)
```

启停：与分类同构——仅状态流转，禁用不删除数据、不影响列表可见性（"商品可选品牌"过滤禁用项的语义在商品模块消费，本 DU 只保证状态准确与禁用后可查询）。
