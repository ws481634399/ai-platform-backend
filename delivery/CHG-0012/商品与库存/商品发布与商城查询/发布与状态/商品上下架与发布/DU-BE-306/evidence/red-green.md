| TC | 红灯失败摘要 | 绿灯通过确认 | 备注 |
| --- | --- | --- | --- |
| TC-001 | AssertionError: status 200 期望但得 404（接口未实现） | ✅ 全绿 | publishDraftProduct |
| TC-002 | AssertionError: status 400 期望但得 404 | ✅ 全绿 | publishWithoutMainImageRejected |
| TC-003 | AssertionError: status 400 期望但得 404 | ✅ 全绿 | publishWithoutEnabledSkuRejected |
| TC-004 | AssertionError: status 400 期望但得 404 | ✅ 全绿 | publishDisabledRejected |
| TC-005 | AssertionError: status 200 期望但得 404 | ✅ 全绿 | unpublishOnSaleProduct |
| TC-006 | AssertionError: status 403 期望但得 404 | ✅ 全绿 | publishPermissionEnforced |
