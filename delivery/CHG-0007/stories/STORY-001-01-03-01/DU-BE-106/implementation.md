# DU Implementation — DU-BE-106

## Status

completed

## Actual Implementation

固定四类 SubjectType，拒绝未知/GUEST Token 主体并隔离 ADMIN 管理入口。

主要文件：
- `mall-common/mall-common-security/src/main/java/com/ai/mall/common/security/SubjectType.java`
- `mall-common/mall-common-security/src/main/java/com/ai/mall/common/security/JwtSubjectConverter.java`

## Verification

- mvn test -q（全量通过）
- TC-001、TC-002、TC-003 均由自动测试、编译/类型检查和代码审查覆盖。

## Deviations

- 实施延续自已开始的工作树，未为每个 TC 单独保留实施前红灯命令输出；未伪造红灯日志，绿灯结果已完整复跑。
- 多个细粒度 Task 共享安全配置与迁移文件，提交按认证、RBAC、前端三个原子能力集收口，DU 与 commit 为多对一。
