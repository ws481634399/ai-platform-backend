-- cart_add.lua（CHG-0018 DU-BE-801）
-- 原子加购：合并累加 + 上限判定 + 滑动 TTL。
-- KEYS[1] = cart:member:{memberId}
-- ARGV    = skuId, addQuantity, ttlSeconds, nowIso, priceFenAtAdded, maxQuantity, maxItems
-- 返回码：0=OK 1=QTY_LIMIT 2=ITEMS_LIMIT
local key = KEYS[1]
local skuId = ARGV[1]
local addQuantity = tonumber(ARGV[2])
local ttlSeconds = tonumber(ARGV[3])
local nowIso = ARGV[4]
local priceFen = tonumber(ARGV[5])
local maxQuantity = tonumber(ARGV[6])
local maxItems = tonumber(ARGV[7])

if redis.call('HEXISTS', key, skuId) == 1 then
    local entry = cjson.decode(redis.call('HGET', key, skuId))
    local merged = entry.quantity + addQuantity
    if merged > maxQuantity then
        return 1
    end
    entry.quantity = merged
    entry.updatedAt = nowIso
    redis.call('HSET', key, skuId, cjson.encode(entry))
else
    if redis.call('HLEN', key) >= maxItems then
        return 2
    end
    local entry = {
        quantity = addQuantity,
        selected = true,
        priceFenAtAdded = priceFen,
        createdAt = nowIso,
        updatedAt = nowIso
    }
    redis.call('HSET', key, skuId, cjson.encode(entry))
end

redis.call('EXPIRE', key, ttlSeconds)
return 0
