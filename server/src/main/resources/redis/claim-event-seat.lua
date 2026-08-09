local mode = redis.call('HGET', KEYS[1], 'mode')

if not mode then
    return -1
end

if mode == 'APPROVAL_REQUIRED' then
    return 2
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -2
end

local remaining = tonumber(redis.call('HGET', KEYS[1], 'remaining'))

if not remaining then
    return -1
end

if remaining <= 0 then
    return 0
end

redis.call('HINCRBY', KEYS[1], 'remaining', -1)
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('XADD', KEYS[3], '*', 'payload', ARGV[2])

return 1
