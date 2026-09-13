# Red-Green Evidence — DU-BE-302

| 阶段 | 证据 | 结果 |
|---|---|---|
| Red | 首跑 CategoryAdminApiTest 11 例中 7 例失败：@PathVariable 缺参数名（A0001）、方法级 @PreAuthorize 拒绝落入全局 500、自引用/循环/404 用例因前述问题误报 | failed |
| Fix | 根 pom 开启 -parameters；新增 ProductSecurityExceptionAdvice 把 AccessDeniedException 映射为 403；修正后重跑 | fixed |
| Green | CategoryRulesTest 7 + CategoryTest 3 + CategoryAdminApiTest 11 全通过；TC-001~TC-010 HTTP/持久化/权限语义覆盖 | passed |
| Regression | mall-identity 50 passed（V3 在 H2 MySQL 模式迁移成功）；mall-gateway 5 passed；mall-common 全量 passed | passed |
