## DU-BE-403 Red-Green 记录

| 阶段 | 结果 | 说明 |
| --- | --- | --- |
| Red | 通过 | InventoryTest.lockAndRelease 验证锁定/释放与可用库存计算 |
| Red | 通过 | InventoryReservationTest.releaseIdempotent 验证释放幂等 |
| Green | 通过 | Inventory.lock/release + InventoryReservation 状态机实现后测试通过 |

并发安全：lockStock 使用 SQL 条件更新 `WHERE (total_quantity - locked_quantity) >= ?`
后端测试：`mvn -pl mall-services/mall-inventory -am test` → 13 tests, 0 failures
