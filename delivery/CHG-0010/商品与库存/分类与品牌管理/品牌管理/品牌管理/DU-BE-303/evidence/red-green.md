# Red-Green Evidence — DU-BE-303

| 阶段 | 证据 | 结果 |
|---|---|---|
| Red 1 | PaginationInnerInterceptor 在 MyBatis-Plus 3.5.12 编译找不到符号（已拆到 mybatis-plus-jsqlparser 模块） | failed |
| Fix 1 | pom 补充 mybatis-plus-jsqlparser（BOM 管理版本），分页插件编译通过 | fixed |
| Red 2 | 大小写同名用例失败：H2 2.x 默认字符串比较大小写敏感，"adidas" 未被识别为 "Adidas" 重名（期望 409 实得 200） | failed |
| Fix 2 | existsByName 改 LOWER(name)=LOWER(?) 参数化比较，跨库对齐 MySQL ci 语义；唯一索引继续并发兜底 | fixed |
| Green | BrandTest 5 + BrandAdminApiTest 9（含 TC-009 并发同名恰一成功）全部通过；分类 21 测试无回归 | passed |
| Regression | mall-product 全量 36 passed、BUILD SUCCESS | passed |
