# DU Task — DU-BE-001

> Repository Delivery 的 DU 级任务细化（Expected Implementation）。
> 权威来源：workspace-source.tasks 中本 DU 小节；本文件是 repo 侧可执行副本，
> Agent 依据权威来源填写，允许按仓内实际情况微调并保持一致。

## 1. Goal

按 design.md §2.2 审计规则集（R1~R4）执行"验收核验三步法"（静态 POM 审计 → dependency:tree 实证 → mvn validate 复验），完成 PRD AC-1~AC-10 逐条留证；若审计违规则执行最小 POM 修复并复验。

## 2. Repository

repo-1（implementation/ai-platform-backend）——Maven 聚合根 `com.ai-mall:backend`，24 个 Reactor 项目。

## 3. Scope

- mall-common/mall-common-core（R1 重点：core 零反向依赖审计）
- mall-common 全部 8 子模块 POM（R2：公共层无横向依赖；R3：test 方向豁免判定）
- mall-contracts 两个子模块 POM（R4：契约纯度）
- 根 POM Reactor（AC-3/AC-4 复验）
- 预期零 POM/代码变更；条件性最小修复预案见 design.md §2.2

## 4. Design References

- workspace design.md §1.2 关键事实 / §2.1 三步法 / §2.2 审计规则集 R1~R4 + 最小修复预案 / §2.3 AC 留证映射表
- workspace prd.md §4 业务规则 / §5 验收标准 AC-1~AC-10
- 无 API / Event / Data Contract 变更（本 DU 不产生接口）

## 5. Dependencies

- 无跨 DU 依赖（单 DU Change）
- 外部条件：JDK 21 + Maven 3.9+ 环境（根 POM enforcer 守门）

## 6. Acceptance Criteria

对应 PRD AC-1~AC-10（全部通过并留证）：

- AC-1 mall-common 8 子模块清单完备
- AC-2 mall-contracts 2 子模块清单完备
- AC-3 10 公共模块全部在 Maven Reactor
- AC-4 构建复验通过（validate + 引用 CHG-0001 全量构建归档）
- AC-5 mall-common 无业务领域模型复验
- AC-6 mall-contracts 无领域与持久化实现复验
- AC-7 无服务间直接 Maven 实现依赖复验
- AC-8 无 Maven 循环依赖复验
- AC-9 mall-common-core 依赖方向审计通过（R1~R4）
- AC-10 证据归档 evidence/ 并有汇总索引

## 7. Implementation Sketch

```text
审计执行流程（repo-1 内）:
1. 静态审计: 逐模块读取 mall-common/*/pom.xml + mall-contracts/*/pom.xml
   → 对照 R1(core 零依赖) / R2(公共层无横向依赖) / R3(test 方向豁免判定) / R4(契约纯度)
2. 依赖树实证:
   mvn dependency:tree -pl mall-common/mall-common-core        → evidence/logs/dependency-tree-core.txt
   mvn dependency:tree -pl mall-contracts/mall-api-contracts   → 契约纯度留证
   mvn dependency:tree -pl mall-contracts/mall-event-contracts → 契约纯度留证
3. 构建复验: mvn validate（根目录，全 24 项目 Reactor）→ evidence/logs/mvn-validate.txt
4. 留证汇总: evidence/ac-verification.md（AC-1~AC-10 复验记录表 + CHG-0001 归档证据引用）
条件分支: 任一规则违规 → 仅删除/修正违规 dependency 声明 → 重跑步骤 2+3 → 重新留证；
         跨模块依赖重构级违规 → 暂停并上报用户决策
```

## 8. Pseudocode

N/A（审计/留证型任务，未命中 business-flow/algorithm/state-transition/orchestration 任一触发器；执行步骤已在 §7 Sketch 中以命令级粒度给出）。

## 9. Verification

- Verification（静态）: grep 审计 mall-common 8 子模块 POM 无 `mall-common-*` 互依声明；core POM `<dependencies>` 为空
- Verification（实证）: dependency-tree-core.txt 输出不含 mall-common-web/security/redis/mq/openfeign/log/test 节点
- Verification（构建）: mvn validate 输出 Reactor Summary 24 项目全 BUILD SUCCESS
- Error Case: validate 失败（enforcer 环境守门触发）→ 记录环境问题单独处理，不视为 AC 失败；审计违规 → 走最小修复预案分支
