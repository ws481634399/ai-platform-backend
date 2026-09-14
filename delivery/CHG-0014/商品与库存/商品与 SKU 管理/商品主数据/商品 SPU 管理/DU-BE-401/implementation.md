# DU Implementation — DU-BE-401

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

- `mall-product`：创建 DTO/Command 新增必填初始 SKU 集合；Controller 完整映射 SKU；Application Service 在既有事务中完成 Product、图片、属性和 SKU 聚合持久化，并校验重复 SKU Code。
- `mall-gateway`：增加 `/api/mall/products/**` 与 `/api/internal/products/**` 到 `mall-product` 的路由；仅商城商品查询匿名开放，内部商品接口仍要求认证。
- `mall-inventory`：`/api/internal/**` 从匿名开放收紧为 `ROLE_SERVICE`；测试安全配置与生产规则保持一致。
- 新增/调整 Product、Gateway、Inventory 接口与安全测试；完整 Maven reactor 共 171 项测试通过。

## Commits

- 开发基线：`cd45af4`。
- 结果 Commit：`6efd0b9`（`fix(m2): close product and inventory acceptance gaps`）。

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
           - 原 DU 建议:
           - 实际实现:
           - 原因:
           - 影响评估: -->

无。

## 自检

- [x] TC-001/TC-002：合法聚合创建成功；空 SKU 被 Bean Validation 拒绝且不产生 Product。
- [x] TC-005/TC-006：Gateway 路由与 public/authenticated 安全边界具有契约测试。
- [x] TC-007：anonymous=401、ADMIN=403、SERVICE=200。
- [x] `mvn test`：24 个 reactor 模块全部成功，171 passed / 0 failed / 0 errors / 0 skipped。
- [x] `git diff --check`：通过。
