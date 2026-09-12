# DU Implementation — DU-BE-101

## Status

completed

## Actual Implementation

以 AdminUser 聚合和 PasswordHash 值对象实现 BCrypt 管理员凭证、唯一用户名持久化与敏感字段隔离。

主要文件：
- `mall-services/mall-identity/src/main/java/com/ai/mall/identity/domain/model/admin/AdminUser.java`
- `mall-services/mall-identity/src/main/java/com/ai/mall/identity/domain/model/admin/PasswordHash.java`
- `mall-services/mall-identity/src/main/java/com/ai/mall/identity/infrastructure/security/BCryptPasswordHasher.java`

## DDD Conformance

- 依赖方向：interfaces → application → domain。
- infrastructure 仅实现 application port 或 domain repository。
- 聚合规则由领域模型维护，应用服务不直接依赖 MyBatis Mapper。

## Verification

- mvn test -q（全量通过，含 DDD 分层架构测试与 H2/Flyway/MyBatis 集成测试）
- LayerDependencyTest 锁定四层边界，IdentityRepositoryIntegrationTest 验证真实持久化适配。

## Deviations

- 原实现存在应用服务直连 Mapper 的设计偏差；本次已完成 DDD 返工并移除旧扁平包。
- 多个细粒度 Task 共享聚合、端口和适配器，DU 与返工提交为多对一。
