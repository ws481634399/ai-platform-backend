# Red-Green Evidence — DU-BE-702

> 商城公开分类树与品牌查询。测试与实现同步编写。

## Red（联调首轮失败基线）

| 模块 | 失败事实 | 根因 |
|---|---|---|
| mall-product | BrandRepositoryImpl 编译失败：`[42,43] 需要 ')' 或 ','` | ECJ 对链式 `.eq(boolean condition, SFunction, Object)` 重载推断失败，导致后续方法引用解析异常 |
| mall-product | BrandAdminApiTest（既有）keyword 测试用例未转义 `%`/`_` | 原 `.like()` 不转义通配符，品牌名含 `%` 时行为不确定 |

修复：page() 拆分为 if 块分别调用 `.eq()` / `.apply()`；keyword 改用 `LOWER(name) LIKE CONCAT('%', LOWER({0}), '%') ESCAPE '!'` + `escapeLike()` 转义 `%`/`_`/`!`。

## Green（转绿结果）

| 模块 | 命令 | 结果 |
|---|---|---|
| mall-product | `mvn -pl mall-services/mall-product clean test` | **75/75**（新增 5 + 既有 70，零回归） |
| mall-gateway | `mvn -pl mall-gateway test` | **19/19**（白名单变更零回归） |

## TC → 测试映射

| TC | 验证落点 | 结果 |
|---|---|---|
| TC-001 分类树仅启用、禁用父整枝剪除（含启用子）、按 sort | MallCatalogApiTest::categoryTreeEnabledOnlyAndPrune | passed |
| TC-002 品牌仅启用、keyword 模糊、size>200 收敛 200、id 字符串 | MallCatalogApiTest::brandsEnabledKeywordAndSizeCap | passed |
| TC-004 空分类树/空品牌返回 [] | MallCatalogApiTest::emptyCategoryTree + emptyBrands | passed |
| — keyword 通配符 % 转义 | MallCatalogApiTest::brandKeywordWildcardEscaped | passed |
