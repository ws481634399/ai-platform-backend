-- cart_update.lua（CHG-0018 DU-BE-801）
-- 原子改量：不存在返回 3；数量范围脚本兜底；写后滑动 TTL。
-- KEYS[1] = cart:member:{memberId}
-- ARGV    = skuId, quantity, ttlSeconds, nowIso, maxQuantity
-- 返回码：0=OK 3=NOT_FOUND
local key = KEYS[1]
local skuId = ARGV[1]
local quantity = tonumber(ARGV[2])
local ttlSeconds = tonumber(ARGV[3])
local nowIso = ARGV[4]
local maxQuantity = tonumber(ARGV[5])

if redis.call('HEXISTS', key, skuId) == 0 then
    return 3
end
if quantity < 1 or quantity > maxQuantity then
    return 1
end

local entry = cjson.decode(redis.call('HGET', key, skuId))
entry.quantity = quantity
entry.updatedAt = nowIso
redis.call('HSET', key, skuId, cjson.encode(entry))
redis.call('EXPIRE', key, ttlSeconds)
return 0
