# Red-Green — DU-BE-604

> 真实过程红基线（失败先于实现/修正观察到），每条含触发命令、失败现象、修正转绿。
> 全量结果：mall-member 79/79；全仓 `mvn clean package` 311/311（0/0/0）。

## RED-1 V2 DDL 在 H2 两处方言不兼容（驱动 DEV-1）

- 触发：V2 按 story-design DDL 写 `IF(is_default = 1, member_id, NULL)) STORED`，
  `mvn -pl mall-services/mall-member test -Dtest=MallMemberApplicationSmokeTest`
- 红：Flyway 迁移失败 JdbcSQLSyntaxError 42001——
  ① `IF(` 处 expected 表达式（H2 MODE=MySQL 不认 MySQL 方言 IF 函数）；
  改 CASE WHEN 后再红：② `STORED` 处 expected `,`/约束子句（H2 计算列无 STORED 关键字）。
- 绿：表达式换标准 `CASE WHEN is_default = 1 THEN member_id ELSE NULL END` 并省略 STORED，
  SmokeTest 上下文加载通过（Flyway 干净库 V1+V2 全应用）。

## RED-2 参数化注解用了非常量表达式 + 注解顺序

- 触发：mvn test-compile
- 红：ShippingAddressTest.java:44 编译错误——`@ValueSource(strings = {"张".repeat(33)})`
  的 repeat() 非编译期常量；另有 @ValueSource 排在 @NullAndEmptySource 之前（JUnit 要求 Null/Empty 源声明在前）。
- 绿：33 字超长拆为独立 @Test（含 32 边界绿断言）；调整注解顺序。

## RED-3 H2 INDEXES 视图元数据列名/索引命名与预期不符（驱动 DEV-5）

- 触发：ShippingAddressApiTest#migrationGeneratedColumnAndUnique
- 红：① `UNIQUE = TRUE` → Column "UNIQUE" not found；改 IS_UNIQUE → Column "is_unique" not found；
  ② 临时打印实测真实行：`{index_name=uk_address_default_INDEX_2, index_type_name=UNIQUE INDEX}`
  ——H2 把约束支持索引自动改名加 `_INDEX_n` 后缀。
- 绿：改查 `INDEX_TYPE_NAME LIKE '%UNIQUE%'` + `INDEX_NAME LIKE 'UK_ADDRESS_DEFAULT%'`；
  冲突异常文案断言同步改小写实际名 `uk_address_default`（断言初版误写不存在的
  hasMessageContainingIgnoringCase，testCompile 报红后修正）。

## RED-4 断言用未来时间常量与 Instant.now() 比较

- 触发：ShippingAddressTest#reviseValidates
- 红：updatedAt（revise 内 Instant.now()，系统日 2026-09-15）早于夹具常量 NOW=2026-09-20，
  isAfterOrEqualTo 失败。
- 绿：断言下界改为 revise 调用前实时采集的 Instant.now()。

## RED-5 ECJ 增量残留桩类导致整类 11 例 Error

- 触发：在 testCompile 曾失败（RED-2）后未 clean 直接 test
- 红：ShippingAddressApiTest 11 例全 `java.lang.Error: Unresolved compilation problems`
  （ECJ 增量编译在部分失败时仍向 test-classes 输出错误桩类）。
- 绿：`mvn clean test` 全量重编后消失；后续验证统一走 clean test / clean package。

## 转绿记录

| 命令 | 范围 | 结果 |
| --- | --- | --- |
| mvn -pl mall-services/member clean test | 模块 | 79/79（新增 35：18+5+1+11） |
| mvn clean package | 24 模块全量回归 | 311/311，0 failures/0 errors/0 skipped，BUILD SUCCESS |
