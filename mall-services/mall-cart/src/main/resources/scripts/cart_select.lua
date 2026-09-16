-- cart_select.lua（CHG-0018 DU-BE-801）
-- 勾选/取消勾选：单条目（不存在返回 3）或全条目；写后滑动 TTL。
-- KEYS[1] = cart:member:{memberId}
-- ARGV    = mode(SINGLE|ALL), skuId(ALL 时忽略), selected(true|false), nowIso, ttlSeconds
-- 返回码：0=OK 3=NOT_FOUND（仅 SINGLE）
local key = KEYS[1]
local mode = ARGV[1]
local selected = ARGV[3] == 'true'
local nowIso = ARGV[4]
local ttlSeconds = tonumber(ARGV[5])

local ids
if mode == 'SINGLE' then
    local skuId = ARGV[2]
    if redis.call('HEXISTS', key, skuId) == 0 then
        return 3
    end
    ids = { skuId }
else
    ids = redis.call('HKEYS', key)
end

for i = 1, #ids do
    local entry = cjson.decode(redis.call('HGET', key, ids[i]))
    entry.selected = selected
    entry.updatedAt = nowIso
    redis.call('HSET', key, ids[i], cjson.encode(entry))
end

if #ids > 0 then
    redis.call('EXPIRE', key, ttlSeconds)
end
return 0
