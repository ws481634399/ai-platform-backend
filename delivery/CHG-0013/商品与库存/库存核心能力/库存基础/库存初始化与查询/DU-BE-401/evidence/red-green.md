## DU-BE-401 Red-Green 记录

| 阶段 | 结果 | 说明 |
| --- | --- | --- |
| Red（测试失败） | 通过 | InventoryTest.initializeSetsQuantities 验证初始化后 total=入参、locked=0、available=total |
| Green（测试通过） | 通过 | Inventory.initialize 实现后测试通过 |
| 重构 | N/A | 无额外重构 |

后端测试：`mvn -pl mall-services/mall-inventory -am test` → 13 tests, 0 failures
