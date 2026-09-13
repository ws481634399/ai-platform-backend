# DU Implementation — DU-BE-304

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

## Commits

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
           - 原 DU 建议:
           - 实际实现:
           - 原因:
           - 影响评估: -->

## 自检

## 交付后联调补全（2026-09-13）

- 现象：集成环境商品列表页 `/api/admin/products/**` 经网关 404，本 DU 交付 ProductAdminController 时网关漏配 products 路由。
- 改动：mall-gateway `src/main/resources/application.yml` 新增 `mall-product-admin-spu` 路由（`Path=/api/admin/products/**`，含 `/{id}/skus` 子资源），URI 复用 `MALL_GATEWAY_PRODUCT_URI` 默认 `http://localhost:8103`。
- 跨服务鉴权：`product:product:*` 权限码经 CHG-0010 补全的 mall-common-security RedisSnapshotAuthorityConverter + 共享 Redis 授权快照生效（mall-product 已接入）。
- 实测：GET /api/admin/products 分页 → 200；GET /api/admin/products/999 → B2141 业务 404。
- 详细回刷见 workspace 仓同 Story implementation.md §5。
