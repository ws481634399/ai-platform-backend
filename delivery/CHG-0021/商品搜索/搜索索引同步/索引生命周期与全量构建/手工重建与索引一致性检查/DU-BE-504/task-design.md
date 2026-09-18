# DU Task Design — DU-BE-504

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

双索引原子切换重建（并发 409/任务状态）、一致性差集检查（截断 200）、admin 端点、identity V9 权限菜单、网关 admin 路由。

## 2. Repository

repo-1（mall-search、mall-identity、mall-gateway）

## 3. Scope

- application.search.SearchIndexRebuildService：
  - 并发闸门：select count RUNNING>0（或重建中唯一约束/行锁）→ B0503 REBUILD_CONFLICT 409 带当前 taskNo；
  - 临时索引 mall_products_rebuild_{yyyyMMddHHmmss}（同 mapping/settings）；
  - 复用 SearchIndexBuildService 分页 bulk；更新 indexed_count/total_count；
  - updateAliases 原子 actions：remove mall_products→旧、add mall_products→新；删除旧物理索引；
  - 成功 SUCCEEDED；失败 FAILED+error_message，临时索引保留（日志记录）。
- interfaces.rest.admin.SearchIndexAdminController：POST /api/admin/search/index/rebuild、GET rebuild-tasks、GET index/consistency-check、GET sync-failures、POST sync-failures/{id}/retry（后两个配合 BE-506，路径预留）。
- 一致性检查：productOnSaleCount=投影分页 total；indexCount=_count；分批 500 terms/scroll 求 productId 集合作差；missing/extra 各截断 200 + truncated。
- mall-identity V9__search_index_permissions.sql：search:index:list/rebuild + 菜单 + 超管授权（沿用 V1..V8 种子范式）。
- mall-gateway：/api/admin/search/** → lb://mall-search（ADMIN 鉴权链）。

## 4. Design References

- CHG-0021 requirement-design.md §2.2（重建/检查）/§2.5（RBAC）；STORY-005-02-01-02 story-design §1/§2/§3。

## 5. Dependencies

权威表：无。实际前置 DU-BE-503。

## 6. Implementation Sketch

```
Admin POST rebuild [search:index:rebuild]
  → RebuildService.start():
      if exists RUNNING task: throw B0503(currentTaskNo)
      task=insert(RUNNING, rebuild_index)
      try:
        create temp index; buildAll(temp);
        old = resolvePhysicalIndices(alias)   // 切换前快照
        es.updateAliases([remove old→alias, add temp→alias])
        deleteIndices(old)
        task.success(total,indexed)
      catch e: task.fail(message); log.error（保留 temp）

consistency-check [list]:
  onSale = projection(page=0,size=1).total
  indexed = es.count("mall_products")
  esIds = scrollAllDocIds(batch 500); dbIds = projectionAllIds(batch 500)
  missing = dbIds - esIds; extra = esIds - dbIds
  return counts + first200(各) + truncated
```

## 7. Pseudocode

```
startRebuild(operator):
  if taskRepo.countRunning() > 0:
      throw REBUILD_CONFLICT(currentRunning.taskNo)       // 409
  task = taskRepo.insert(newTask("mall_products_rebuild_"+ts, RUNNING))
  try:
      es.createIndex(task.physicalIndex, PRODUCT_MAPPING)
      buildService.fullBuild(task.physicalIndex, task)    // 进度回写
      oldIndices = es.indicesForAlias("mall_products")
      es.updateAliases(remove(oldIndices), add(task.physicalIndex))
      es.deleteIndices(oldIndices)
      taskRepo.markSuccess(task.id)
  catch e:
      taskRepo.markFailed(task.id, truncate(e.message,1000))
  return task
```
