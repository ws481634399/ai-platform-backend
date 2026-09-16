package com.ai.mall.cart.domain.cart;

import java.util.List;

/**
 * 购物车仓储端口（CHG-0018 DU-BE-801）：Redis Hash 写模型。
 *
 * <p>所有写操作必须在同一段 Lua 内完成数据变更与 EXPIRE 滑动续期；
 * 脚本返回码语义见 {@link CartScriptCode}。key 按 memberId 物理隔离，
 * 仓储不做任何鉴权（memberId 由应用层保证来自 SecurityContext）。
 */
public interface CartRepository {

    /**
     * 加购：已存在则数量累加（超 999 拒绝且保持原值），新条目默认勾选；
     * 条目数达 100 时拒绝新 SKU。
     *
     * @param priceFenAtAdded 加购时刻售价快照（整数分，来自 product 契约）
     */
    CartScriptCode add(long memberId, long skuId, int quantity, long priceFenAtAdded);

    /** 改量：条目不存在返回 {@link CartScriptCode#NOT_FOUND}。 */
    CartScriptCode updateQuantity(long memberId, long skuId, int quantity);

    /** 单条删除：幂等（不存在视为成功）。 */
    void remove(long memberId, long skuId);

    /** 批量删除：幂等，不存在项忽略。 */
    void removeBatch(long memberId, List<Long> skuIds);

    /** 设置单条目勾选状态：条目不存在返回 NOT_FOUND。 */
    CartScriptCode selectOne(long memberId, long skuId, boolean selected);

    /** 设置全部条目勾选状态（空车为 no-op 成功）。 */
    void selectAll(long memberId, boolean selected);

    /** 读取全部原始条目（field 顺序不保证）；空车返回空列表。 */
    List<CartItem> findItems(long memberId);
}
