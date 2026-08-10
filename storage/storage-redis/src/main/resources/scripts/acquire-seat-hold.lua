local nowMillis = tonumber(ARGV[1])
local expiresAtMillis = tonumber(ARGV[2])
local ttlMillis = tonumber(ARGV[3])
local maxSeats = tonumber(ARGV[4])
local holdId = ARGV[5]
local scheduleId = ARGV[6]
local memberId = ARGV[7]
local seatIds = ARGV[8]

redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', nowMillis)
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', nowMillis)

local requestedSeats = #KEYS - 3
if redis.call('ZCARD', KEYS[2]) + requestedSeats > maxSeats then
    return -2
end

for index = 4, #KEYS do
    if redis.call('EXISTS', KEYS[index]) == 1 then
        return -1
    end
end

for index = 4, #KEYS do
    local seatId = string.match(KEYS[index], 'seat:(%d+)$')
    redis.call('SET', KEYS[index], holdId, 'PX', ttlMillis)
    redis.call('ZADD', KEYS[1], expiresAtMillis, seatId)
    redis.call('ZADD', KEYS[2], expiresAtMillis, seatId)
end

redis.call('HSET', KEYS[3],
    'scheduleId', scheduleId,
    'memberId', memberId,
    'seatIds', seatIds,
    'expiresAt', ARGV[9])
redis.call('PEXPIRE', KEYS[3], ttlMillis)

local scheduleTtl = redis.call('PTTL', KEYS[1])
if scheduleTtl < ttlMillis then
    redis.call('PEXPIRE', KEYS[1], ttlMillis)
end

local memberTtl = redis.call('PTTL', KEYS[2])
if memberTtl < ttlMillis then
    redis.call('PEXPIRE', KEYS[2], ttlMillis)
end

return 1
