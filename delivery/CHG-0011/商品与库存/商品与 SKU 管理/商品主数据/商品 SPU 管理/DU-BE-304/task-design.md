# DU Task Design — DU-BE-304

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"
> 权威 DU 划分：story-design.md §5（SSOT）
> 本文件固定为 Expected Implementation，与 implementation.md 分立。

## 1. Goal

商品权限码注册（mall-identity V3）+ Product 聚合、SPU 管理端 API、商品图片与属性、状态生命周期、领域事件。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-identity: V3 权限/菜单 SQL
- mall-product: domain.product（Product/ProductImage/ProductAttribute/ProductStatus）、ProductRepository 端口与实现、ProductAdminAppService、ProductAdminController（/api/admin/products）

## 4. Design References

- requirement-design.md §2 提议方案、§3 仓库影响、§6 DU 划分
- story-design.md §1 模块改动、§2 接口契约、§3 数据变更

## 5. Dependencies

CHG-0010（分类/品牌表、安全配置、网关路由）

## 6. Implementation Sketch

- Flyway V2 建 product_spu/product_image/product_attribute；V3(identity) 注册 product:product:* 权限与商品菜单。
- Product 聚合根：create() 校验 categoryId/brandId 存在启用、productCode 唯一、状态 DRAFT；updateBasicInfo/changeCategory/changeBrand/changeStatus/setMainImage。
- ProductRepository：findById/findByCode/save，MyBatis-Plus 持久化 product_spu + 级联 images/attributes。
- ProductAdminAppService：create/update/changeStatus/get/page，事务注解，调用 categoryBrand 存在性校验。
- Controller：REST + @PreAuthorize hasAuthority。

## 7. Pseudocode

N/A（流程为标准 CRUD，无复杂业务流/算法/状态机编排；状态流转仅 DRAFT↔DISABLED，简单赋值+校验）
