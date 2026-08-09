local pending = redis.call(
        'XPENDING',
        KEYS[1],
        ARGV[1],
        ARGV[2],
        ARGV[2],
        1
)

if #pending == 0 then
    return 0
end

redis.call(
        'XADD',
        KEYS[2],
        '*',
        'originalStreamId', ARGV[2],
        'payload', ARGV[3],
        'attempts', ARGV[4],
        'failureType', ARGV[5],
        'failureMessage', ARGV[6],
        'failedAt', ARGV[7]
)

return redis.call('XACK', KEYS[1], ARGV[1], ARGV[2])
