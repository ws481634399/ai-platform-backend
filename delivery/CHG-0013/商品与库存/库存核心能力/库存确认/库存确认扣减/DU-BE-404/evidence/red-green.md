## DU-BE-404 Red-Green 记录

| 阶段 | 结果 | 说明 |
| --- | --- | --- |
| Red | 通过 | InventoryTest.confirmDeduction 验证 total 和 locked 同时扣减 |
| Red | 通过 | InventoryReservationTest.confirmIdempotent 验证确认扣减幂等 |
| Green | 通过 | Inventory.confirmDeduction + InventoryReservation.confirmDeduction 实现后测试通过 |

后端测试：`mvn -pl mall-services/mall-inventory -am test` → 13 tests, 0 failures
