package com.ai.mall.search.application.index;

import com.ai.mall.search.application.index.projection.ProductProjectionView;

/**
 * 失败重放的写执行端口（application 层小接口）：由同步接收服务实现，
 * 重试服务重拉投影后经此写 ES，STALE_VERSION 同样视为成功（更新版本已在）。
 */
public interface SyncWriteExecutor {

    /** 按当前投影 upsert；过期版本静默成功。 */
    void upsertLatest(ProductProjectionView view);

    /** 明确删除（文档不存在幂等成功）。 */
    void deletePlain(long productId);
}
