local mode = redis.call('HGET', KEYS[1], 'mode')

if not mode then
    return -1
end

if mode == 'APPROVAL_REQUIRED' then
    return 2
end

local maxCapacity = tonumber(ARGV[1])
local claimed = redis.call('SCARD', KEYS[2])
local remaining = maxCapacity - claimed

if remaining < 0 then
    remaining = 0
end

redis.call('HSET', KEYS[1], 'remaining', remaining)

return remaining
