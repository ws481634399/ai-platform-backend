# DU Task Design — DU-BE-302

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 requirement-design.md §6（SSOT）；本文件只按 DU id DU-BE-302 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-302 与同目录 task-spec.md（"做什么/验收"）配对互链。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

打通"网关 → mall-product"网络与鉴权链路、登记分类/品牌 8 个权限码与 3 个菜单（RBAC），并实现分类主数据后端能力与全部层级不变量（requirement-design.md §6 / DU-BE-302 goal 原文）。

## 2. Repository

repo-1（ai-platform-backend，单体多模块 Maven 仓；本 DU 触达 mall-identity、mall-product、mall-gateway 三个模块）。

## 3. Scope

- mall-identity：`mall-services/mall-identity/src/main/resources/db/migration/V2__add_product_category_brand_permissions.sql`（新增）。
- mall-product：
  - `pom.xml`（增 mall-common-security 依赖）。
  - `src/main/resources/application*.yml`（jwt public-key-location 等配置）。
  - `src/main/resources/db/migration/V1__create_product_category_and_brand.sql`（本 DU 只写 product_category 段，DU-BE-303 追加 brand 段）。
  - `infrastructure/config/ProductSecurityConfiguration.java`（新增）。
  - `infrastructure/persistence/category/`：po/CategoryPO、mapper/CategoryMapper、CategoryRepositoryImpl、CategoryTreeAssembler。
  - `domain/category/`：Category、CategoryLevel、CategoryTree、CategoryRepository、CategoryException。
  - `application/category/CategoryApplicationService.java`。
  - `interfaces/rest/admin/CategoryAdminController.java` 与 dto/category/*。
  - `src/test/java/.../category/`：领域单测与 API 集成测试。
- mall-gateway：`src/main/resources/application.yml`（增 categories/brands 静态路由 + 本地 profile 覆写项）。

## 4. Design References

- requirement-design.md §2 提议方案（DDD 四层划分、product_category 表结构）。
- requirement-design.md §4 跨仓协作契约（路径 SSOT `/api/admin/categories`、权限码 SSOT `product:category:*`/`product:brand:*`、UnifyResult、401/403 JSON）。
- requirement-design.md §5.1 公共组件与共享契约；§6 / DU-BE-302 权威行。
- story-design.md §1.1（模块清单）、§2（接口契约与移动判定算法）、§3（迁移）、§4（错误处理）。
- 既有实现参照：`mall-identity/.../infrastructure/config/JwtConfiguration.java`、V1 RBAC 种子 SQL。

## 5. Dependencies

无（权威表 DU-BE-302 depends on 为 —）。DU-BE-303 与 DU-FE-301 依赖本 DU。

## 6. Implementation Sketch

公共前置装配：

```text
mall-gateway(8080, GatewaySecurityConfiguration 已存在)
  └─ route: Path=/api/admin/categories/**,/api/admin/brands/**
        uri: ${mall.gateway.product-uri:lb://mall-product}   # 本地 profile 覆写 http://localhost:8103
mall-product(8103)
  └─ ProductSecurityConfiguration
       ├─ NimbusJwtDecoder(public-key-location，复用 identity 同把验签公钥)
       ├─ JwtSubjectConverter → authorities: permissions claim + ROLE_<type>
       ├─ SecurityFilterChain: /api/admin/** → hasRole('ADMIN')，401/403 走 JSON AuthenticationEntryPoint/AccessDeniedHandler
       └─ @EnableMethodSecurity（业务接口用 hasAuthority 细粒度判定）
mall-identity DB(mall_identity)
  └─ Flyway V2：8 权限码 + 3 菜单（商品管理目录/分类页/品牌页）+ 超管角色授权（子查询按 role code）
```

分类业务调用链：

```text
CategoryAdminController (/api/admin/categories, @PreAuthorize hasAuthority)
  → CategoryApplicationService (@Transactional)
      → Category 聚合（构造/rename/move/disable，内聚不变量）
      → CategoryRepository(端口) → CategoryRepositoryImpl → CategoryMapper(MyBatis-Plus)
      → CategoryTreeAssembler（平铺 → 树）
  ← UnifyResult.ok / BusinessException → GlobalExceptionHandler
```

数据流：请求 DTO（validation 注解）→ 应用服务编排与领域校验 → PO 落库（审计字段 MyBatis-Plus 自动填充/显式设置）→ View DTO 返回。错误处理：六类不变量违规抛 CategoryException（BusinessException 子类，模块错误码 PRODUCT_CATEGORY_*）；(parent_id,name) 唯一索引并发冲突捕获 DuplicateKeyException 转同名业务错误。

## 7. Pseudocode

创建分类：

```text
createCategory(req):
  name = trim(req.name); 非空与长度 1..32 已由 @Valid 保证
  parentId = req.parentId == null ? 0 : req.parentId
  if parentId == 0:
      level = 1
  else:
      parent = repository.findById(parentId)
      guard parent != null else throw CATEGORY_PARENT_NOT_FOUND
      guard parent.status == ENABLED else throw CATEGORY_PARENT_DISABLED
      guard parent.level < 3 else throw CATEGORY_LEVEL_EXCEEDED   // 父在 3 级则子为 4 级
      level = parent.level + 1
  guard !repository.existsSiblingName(parentId, name, excludeId=null)
        else throw CATEGORY_NAME_DUPLICATED   // 唯一索引为最终兜底
  category = Category.create(name, parentId, level, req.sort)
  try: repository.insert(category)
  catch DuplicateKeyException: throw CATEGORY_NAME_DUPLICATED
  return category.id
```

移动分类（PUT 改 parentId；name/sort 同接口更新）：

```text
moveCategory(id, req):
  self = repository.findById(id) else throw NOT_FOUND
  newParentId = req.parentId（0 表示移到根）
  if newParentId == self.id: throw CATEGORY_SELF_REFERENCE
  if newParentId != 0:
      target = repository.findById(newParentId) else throw PARENT_NOT_FOUND
      guard target.status == ENABLED else throw PARENT_DISABLED
      # 循环检测：沿 target 祖先链向上，命中 self.id 即环
      ancestorId = target.parentId; hops = 0
      depthOfTarget = target.level
      while ancestorId != 0 and hops <= 3:
          if ancestorId == self.id: throw CATEGORY_CYCLE
          ancestorId = repository.findById(ancestorId).parentId; hops++
      # 层级校验：新父深度 + 本子树最大相对深度 ≤ 3
      subtree = repository.loadSubTree(self.id)
      maxRelativeDepth = max(node.level - self.level for node in subtree)
      guard target.level + 1 + maxRelativeDepth <= 3 else throw LEVEL_EXCEEDED
  # 名称变更走同级唯一校验（parentId 可能同时变化）
  guard !existsSiblingName(newParentId, trim(req.name), excludeId=self.id) else NAME_DUPLICATED
  levelDelta = (newParentId == 0 ? 1 : target.level + 1) - self.level
  self.update(trim(req.name), newParentId, newLevel, req.sort)
  repository.update(self)
  if levelDelta != 0: repository.shiftSubtreeLevels(self.id, levelDelta)  # 同事务批量重算
```

启停：

```text
changeStatus(id, status):
  category = findById else NOT_FOUND
  status == DISABLED ? category.disable() : category.enable()   // 仅状态流转，不加载/级联子树
  repository.update(category)
```

树查询：findAll 一次平铺加载（分类量级小）→ 按 parentId 内存分组组装 → 每层按 (sort, id) 排序 → 返回含禁用节点的完整树。
