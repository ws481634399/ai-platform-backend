# Commits — DU-BE-702

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。

| 短引用 | 完整 Hash | 消息 |
|---|---|---|
| 代码提交 | b4b5a1f | feat(product): 公开分类树与品牌查询接口 + 网关白名单 (DU-BE-702) |

提交内容：MallCategoryController（启用分类树、禁用整枝剪除）、MallBrandController（仅 ENABLED、keyword 模糊转义、size≤200）、MallCatalogDtos + MallCategoryTreeAssembler、BrandApplicationService.mallPage、CategoryApplicationService.mallTree、BrandRepositoryImpl.page LIKE ESCAPE 改造；网关路由 + 白名单扩展；5 例集成测试。
