## DU-BE-402 Red-Green 记录

| 阶段 | 结果 | 说明 |
| --- | --- | --- |
| Red | 通过 | InventoryTest.adjustUpdatesTotal 验证正数加、负数减 |
| Red | 通过 | InventoryTest.adjustRejectsNegativeResult 验证负库存拒绝 |
| Green | 通过 | Inventory.adjust 实现后测试通过 |

后端测试：`mvn -pl mall-services/mall-inventory -am test` → 13 tests, 0 failures
