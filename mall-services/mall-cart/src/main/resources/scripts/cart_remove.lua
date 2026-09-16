-- cart_remove.lua（CHG-0018 DU-BE-801）
-- 单条/批量删除：HDEL 天然幂等；key 仍存在时才滑动续期，避免为已清空的车重建 TTL。
-- KEYS[1] = cart:member:{memberId}
-- ARGV    = ttlSeconds, skuId1, skuId2, ...
local key = KEYS[1]
local ttlSeconds = tonumber(ARGV[1])

if #ARGV >= 2 then
    local fields = {}
    for i = 2, #ARGV do
        fields[#fields + 1] = ARGV[i]
    end
    redis.call('HDEL', key, unpack(fields))
end

if redis.call('EXISTS', key) == 1 then
    redis.call('EXPIRE', key, ttlSeconds)
end
return 0
