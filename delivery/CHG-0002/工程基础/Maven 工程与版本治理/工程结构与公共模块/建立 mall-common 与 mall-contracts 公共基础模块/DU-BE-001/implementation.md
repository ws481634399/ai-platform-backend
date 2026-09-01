# DU Implementation — DU-BE-001

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库，Workspace 仅保留引用。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task.md（Expected Implementation）分立，不得合并。

## 变更内容

**验收核验型 DU：POM/代码/配置零变更（task.md 预期成立）。**

按 task.md §7 Implementation Sketch 执行"验收核验三步法"，产出为审计留证（按 design §3 约定归档于 workspace 仓 evidence/，不入库本仓库）：

| 步骤 | 内容 | 结果 |
|------|------|------|
| Step 1 静态 POM 审计 | R1~R4 规则集（design §2.2）逐条执行：8 个 common 子模块 POM、2 个 contracts POM、8 个服务 POM、公共层/契约层源码扫描 | 零违规 |
| Step 2 依赖树实证 | `dependency:tree`（mall-common-core + 两 contracts 模块，覆盖传递依赖路径） | 三棵树均仅含模块自身坐标（零依赖） |
| Step 3 构建复验 | `mvn validate` 全 24 项目 Reactor（enforcer Maven 3.9+ / JDK [21,22) 守门） | 24/24 BUILD SUCCESS |

- 修改的工程文件：**无**
- AC 复验：AC-1 ~ AC-10 全部通过，最小修复预案未触发
- 审计证据归档（workspace 仓 `delivery/changes/CHG-0002/工程基础/Maven 工程与版本治理/工程结构与公共模块/建立 mall-common 与 mall-contracts 公共基础模块/evidence/`）：
  - `ac-verification.md`（规则集执行记录 + AC 复验记录表 + 审计明细 + 证据索引）
  - `logs/mvn-validate.txt`、`logs/dependency-tree-core.txt`、`logs/dependency-tree-api-contracts.txt`、`logs/dependency-tree-event-contracts.txt`
  - `evidence.yaml`（5 条 test-run，EV-001~EV-005，covers 覆盖 AC-1~AC-10）

## Commits

无新增 Commit（核验型 DU，工程零变更）。

DU baseline = result = `350304b`（CHG-0001 交付终点基线）——本 Change 验证该基线满足 ENG-BASE-002 全部验收标准，基线未漂移。

## Deviations

### DEV-1
- 原 DU 建议: task.md §7 直接执行 `mvn dependency:tree -pl mall-common/mall-common-core`（默认本地仓库）
- 实际实现: `mvn -B "-Dmaven.repo.local=D:/Desktop/ai-platform/.m2-sandbox" -pl ... dependency:tree`（本地仓库临时指向 workspace 内 `.m2-sandbox`，验证完成后已删除）
- 原因: Trae 沙箱禁止向工作区外目录写入（`D:\maven-repository`），默认仓库模式下 maven-dependency-plugin 下载被拦截；且 `-D` 参数经 PowerShell/沙箱命令链路解析需加双引号，否则被截断失效
- 影响评估: 仅命令执行环境差异；dependency:tree 的依赖解析逻辑与审计结论不受影响（`mvn validate` 仍按默认仓库模式执行成功）。留证文件中已注明该环境适配

## 自检（sdd-dev §5 清单 + task.md §9 Verification）

- [x] DU 已物化并在 Scope 内实施（repo-1 单 DU，无跨仓改动）
- [x] 实施前已读 repo task.md §7/§8/§9，未默默改道；偏离已记录（DEV-1）
- [x] Verification（静态）: mall-common 8 子模块 POM 无 `mall-common-*` 互依声明；core POM 无 `<dependencies>` 段 — **通过**
- [x] Verification（实证）: dependency-tree-core.txt 输出不含 mall-common-web/security/redis/mq/openfeign/log/test 节点（树仅自身一行）— **通过**
- [x] Verification（构建）: mvn validate Reactor Summary 24 项目全 BUILD SUCCESS — **通过**
- [x] Error Case 分支未触发: validate 成功（enforcer 守门通过）、审计零违规，最小修复预案未启用
- [x] 每个 Commit 对应一个 Task：无新增 Commit（核验型，见 §Commits）
- [x] 无未完成 Task、无未 catch 的异步错误、无硬编码敏感信息（密码/密钥）
- [x] 代码遵循 standards/ 编码规范：不适用（零代码变更）
- [x] DU evidence 与 workspace 聚合记录一致（EV-001~EV-005）
