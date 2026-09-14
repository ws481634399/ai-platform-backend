# DU Task Design — DU-BE-401

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-401 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-401 与同目录 task-spec.md（"做什么/验收"）配对互链。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

修复 Product 聚合创建事务、Product 网关可达性和 Inventory 内部写接口的 SERVICE 授权。

## 2. Repository

repo-1（`implementation/ai-platform-backend`）。

## 3. Scope

- `mall-services/mall-product`：CreateProductRequest、ProductApplicationService、ProductAdminController 及测试。
- `mall-gateway`：Mall/Internal Product 路由与安全规则。
- `mall-services/mall-inventory`：InventorySecurityConfiguration 和 internal security tests。

## 4. Design References

- `requirement-design.md` §2（方案）、§4（跨仓契约）、§6（DU）。
- `story-design.md` §1（模块改动）、§2（接口契约）。

## 5. Dependencies

无。

## 6. Implementation Sketch

1. 先为聚合创建、Gateway 路由和 Inventory 授权添加失败测试。
2. Controller 将 `skus[]` 转为创建参数，Application Service 在现有 `@Transactional` 边界内依次持久化 Product/图片/属性/SKU；任一校验或持久化异常自动回滚。
3. Gateway 路由将 mall/internal 路径转发到 mall-product，仅 mall 路径免认证。
4. Inventory JWT converter 为 SERVICE 主体提供 `ROLE_SERVICE`，internal 路径改为 `hasRole("SERVICE")`。

## 7. Pseudocode

N/A；本 DU 不引入新算法或状态机，事务顺序已在 §6 明确。
