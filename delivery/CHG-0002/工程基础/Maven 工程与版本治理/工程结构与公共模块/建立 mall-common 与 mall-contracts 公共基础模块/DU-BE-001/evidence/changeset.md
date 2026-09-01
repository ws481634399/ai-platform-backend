# Changeset — DU-BE-001（CHG-0002）

> DU 级修改文件清单表。核验型 DU：本仓库 POM/代码/配置零变更，无 changeset 条目。

| 仓库 | 模块 | 文件 | 变更类型 | 行数变化 |
| ---- | ---- | ---- | -------- | -------- |
| （无） | — | — | 零变更（验收核验型） | ±0 |

**零变更结论**：git status 显示本仓库除未跟踪的 `delivery/`（SDD 流程文档）外无任何工程文件改动；HEAD 保持 `350304b`（CHG-0001 交付终点）。

审计留证产物（按 design §3 约定归档于 workspace 仓，不入库本仓库）：

| 位置（workspace 仓相对路径） | 说明 |
| --- | --- |
| `delivery/changes/CHG-0002/工程基础/Maven 工程与版本治理/工程结构与公共模块/建立 mall-common 与 mall-contracts 公共基础模块/evidence/ac-verification.md` | AC-1~AC-10 复验记录 + R1~R4 规则执行记录 |
| 同目录 `evidence/logs/mvn-validate.txt` | mvn validate 全量 Reactor 输出（24/24 SUCCESS） |
| 同目录 `evidence/logs/dependency-tree-core.txt` | core 依赖树（零依赖，AC-9 核心证据） |
| 同目录 `evidence/logs/dependency-tree-api-contracts.txt` | API 契约依赖树（零依赖） |
| 同目录 `evidence/logs/dependency-tree-event-contracts.txt` | 事件契约依赖树（零依赖） |
| 同目录 `evidence/evidence.yaml` | 5 条 test-run 结构化证据（EV-001~EV-005） |
