package com.ai.mall.search.domain.index;

import com.ai.mall.search.application.index.projection.ProductProjectionView;
import java.util.List;
import java.util.Set;

/**
 * 搜索索引出站端口（CHG-0021）：封装 ES 索引生命周期、版本化写入与全量 id 读取。
 */
public interface SearchIndexPort {

    /** 以冻结商品 Mapping 建物理索引；镜像无 IK 分词插件时回退 standard 映射。 */
    void createProductIndex(String physicalIndex);

    boolean indexExists(String physicalIndex);

    /** 别名当前指向的全部物理索引。 */
    Set<String> physicalIndicesOf(String alias);

    void putAlias(String physicalIndex, String alias);

    /** 原子别名切换：摘除旧物理索引、挂接新索引（单 actions 请求，查询不中断）。 */
    void switchAlias(String alias, Set<String> oldIndices, String newIndex);

    void deleteIndices(Set<String> indices);

    /** 全量构建批量写入（external_gte 版本=updatedAt 毫秒）。 */
    void bulkUpsert(String physicalIndex, List<ProductProjectionView> views);

    /** 单条版本化 upsert；过期事件（版本冲突）返回 STALE_VERSION，调用方按成功消化。 */
    IndexWriteResult upsertVersioned(ProductProjectionView view);

    /** 带版本保护的删除（下架同步）；版本冲突返回 STALE_VERSION 不删除。 */
    IndexWriteResult deleteVersioned(ProductProjectionView view);

    /** 硬删除（人工/重试明确删除）；文档不存在视为成功。 */
    void deletePlain(long productId);

    long count(String alias);

    /** search_after 分批读取别名下全部 docId。 */
    List<String> allIds(String alias, int batchSize);

    /** 写入结果。 */
    enum IndexWriteResult {
        WRITTEN,
        STALE_VERSION
    }
}
