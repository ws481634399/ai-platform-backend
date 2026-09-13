# DU Task Design — DU-BE-306

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"
> 权威 DU 划分：story-design.md §5（SSOT）

## 1. Goal

Product 聚合 publish/unpublish 行为 + 上架校验 + 领域事件 + 管理端 publish/unpublish API + product:product:publish 权限码。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-identity: V5 注册 product:product:publish 权限码
- mall-product: domain.product.Product.publish/unpublish、领域事件 ProductPublished/Unpublished、ProductApplicationService.publish/unpublish、ProductAdminController publish/unpublish 端点

## 4. Design References

- requirement-design.md §2 提议方案、§6 DU 划分
- story-design.md §1 模块改动、§2 接口契约、§3 数据变更

## 5. Dependencies

CHG-0011（Product 聚合、ProductStatus、SKU 实体、权限体系）

## 6. Implementation Sketch

- mall-identity V5：INSERT product:product:publish 权限码。
- Product 聚合：publish() 校验主图存在、至少一 ENABLED SKU、ENABLED SKU 价格>=0、非 DISABLED；通过则 status=ON_SALE 并注册 ProductPublishedDomainEvent。unpublish() 将 ON_SALE 改为 OFF_SALE 并注册 ProductUnpublishedDomainEvent。
- ProductApplicationService：publish(id) 加载 Product，调用 publish()，持久化；unpublish(id) 同理。
- ProductAdminController：POST /{id}/publish、POST /{id}/unpublish，@PreAuthorize product:product:publish。

## 7. Pseudocode

N/A（上架校验为条件判断集合，无复杂状态机编排；状态流转为直接赋值）
