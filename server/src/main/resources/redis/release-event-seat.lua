local mode = redis.call('HGET', KEYS[1], 'mode')

if not mode then
    return -1
end

if mode == 'APPROVAL_REQUIRED' then
    return 2
end

local removed = redis.call('SREM', KEYS[2], ARGV[1])

if removed == 0 then
    return 0
end

redis.call('HINCRBY', KEYS[1], 'remaining', 1)

return 1
