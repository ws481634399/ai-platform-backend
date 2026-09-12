# Red-Green Evidence — DU-BE-301

| 阶段 | 证据 | 结果 |
|---|---|---|
| Red | 架构审查发现 application/service 直接依赖 MyBatis Mapper，违反既定 DDD 依赖方向 | failed |
| Green | LayerDependencyTest：领域层零框架依赖、应用层零基础设施依赖、接口层零 MyBatis 依赖 | passed |
| Regression | mvn test -q（全量通过，含 DDD 分层架构测试与 H2/Flyway/MyBatis 集成测试） | passed |
