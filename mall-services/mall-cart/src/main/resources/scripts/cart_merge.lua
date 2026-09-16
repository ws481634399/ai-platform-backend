-- cart_merge.lua（CHG-0018 DU-BE-803）
-- 原子合并：token 校验+消费（DEL）+ 游客车并入会员车（相加/截断/dropped）+ 滑动 TTL。
-- KEYS[1] = cart:member:{memberId}
-- KEYS[2] = cart:merge:{token}
-- ARGV[1] = memberId（字符串，用于 token 值校验）
-- ARGV[2] = nowIso
-- ARGV[3] = ttlSeconds（车 TTL）
-- ARGV[4] = maxQuantity（999）
-- ARGV[5] = maxItems（100）
-- ARGV[6..] = 每游客条目 4 个参数：skuId, quantity, selected, priceFenAtAdded
-- 返回 JSON 字符串：{merged:[{skuId,quantity}], truncated:[{skuId,finalQuantity}], dropped:[{skuId,reason}]}
-- 特殊返回："TOKEN_MISSING" / "TOKEN_MISMATCH"

local cartKey = KEYS[1]
local tokenKey = KEYS[2]
local expectedMemberId = ARGV[1]
local nowIso = ARGV[2]
local ttlSeconds = tonumber(ARGV[3])
local maxQuantity = tonumber(ARGV[4])
local maxItems = tonumber(ARGV[5])

-- 把 Lua 数组编码为 JSON 数组字符串（空表 → "[]"，避免 cjson 把空表编成 "{}"）
local function encodeArray(t)
    local parts = {}
    for i, v in ipairs(t) do
        parts[i] = cjson.encode(v)
    end
    return "[" .. table.concat(parts, ",") .. "]"
end

-- 1. 校验 token：必须存在且值等于 memberId
local tokenValue = redis.call('GET', tokenKey)
if not tokenValue then
    return "TOKEN_MISSING"
end
if tokenValue ~= expectedMemberId then
    return "TOKEN_MISMATCH"
end

-- 2. 消费 token（先删保证幂等：重放必 TOKEN_MISSING 不累加）
redis.call('DEL', tokenKey)

-- 3. 逐条合并
local merged = {}
local truncated = {}
local dropped = {}

local argIndex = 6
while argIndex <= #ARGV do
    local skuId = ARGV[argIndex]
    local addQuantity = tonumber(ARGV[argIndex + 1])
    local selected = ARGV[argIndex + 2] == "true"
    local priceFen = tonumber(ARGV[argIndex + 3])
    argIndex = argIndex + 4

    if redis.call('HEXISTS', cartKey, skuId) == 1 then
        -- 会员已有：数量相加，超 999 截断
        local entry = cjson.decode(redis.call('HGET', cartKey, skuId))
        local finalQty = entry.quantity + addQuantity
        if finalQty > maxQuantity then
            finalQty = maxQuantity
            table.insert(truncated, {skuId = skuId, finalQuantity = finalQty})
        end
        entry.quantity = finalQty
        entry.updatedAt = nowIso
        -- 选中态合并：任一方选中即为选中
        entry.selected = entry.selected or selected
        redis.call('HSET', cartKey, skuId, cjson.encode(entry))
        table.insert(merged, {skuId = skuId, quantity = finalQty})
    else
        -- 新 SKU：检查条目上限
        if redis.call('HLEN', cartKey) >= maxItems then
            table.insert(dropped, {skuId = skuId, reason = "CART_ITEMS_LIMIT"})
        else
            local entry = {
                quantity = addQuantity,
                selected = selected,
                priceFenAtAdded = priceFen,
                createdAt = nowIso,
                updatedAt = nowIso
            }
            redis.call('HSET', cartKey, skuId, cjson.encode(entry))
            table.insert(merged, {skuId = skuId, quantity = addQuantity})
        end
    end
end

-- 4. 滑动 TTL（有合并或车已存在则续期）
if #merged > 0 or redis.call('EXISTS', cartKey) == 1 then
    redis.call('EXPIRE', cartKey, ttlSeconds)
end

return '{"merged":' .. encodeArray(merged)
        .. ',"truncated":' .. encodeArray(truncated)
        .. ',"dropped":' .. encodeArray(dropped) .. '}'
