# Commits — DU-BE-705

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。

| 短引用 | 完整 Hash | 消息 |
|---|---|---|
| 代码提交 | b982bf5 | feat(inventory,product): SKU 可售状态三态聚合与降级 |

提交内容：inventory 内部 availability 批量精确数量端点；product 公开三态端点（阈值 10、白名单 DTO 无数量、故障降级 UNKNOWN）；InventoryAvailabilityClient；两模块错误码与校验；inventory 4 + product 6 共 10 例测试。
