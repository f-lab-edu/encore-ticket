local sequenceKey = KEYS[1]
local waitingKey = KEYS[2]
local expiryKey = KEYS[3]
local memberKey = KEYS[4]

local nowMillis = tonumber(ARGV[1])
local candidateToken = ARGV[2]
local memberId = ARGV[3]
local keyPrefix = ARGV[4]
local graceMillis = tonumber(ARGV[5])
local maxLapses = tonumber(ARGV[6])
local hardExpiryMillis = tonumber(ARGV[7])
local cleanupSlackMillis = tonumber(ARGV[8])

local function int(value)
    return string.format('%d', value)
end

local function forget(token)
    local tokenKey = keyPrefix .. ':token:' .. token
    local owner = redis.call('HGET', tokenKey, 'memberId')
    if owner then
        local ownerKey = keyPrefix .. ':member:' .. owner
        if redis.call('GET', ownerKey) == token then
            redis.call('DEL', ownerKey)
        end
    end
    redis.call('DEL', tokenKey)
    redis.call('ZREM', waitingKey, token)
    redis.call('ZREM', expiryKey, token)
end

local stale = redis.call('ZRANGEBYSCORE', expiryKey, '-inf', '(' .. int(nowMillis))
for index = 1, #stale do
    forget(stale[index])
end

local function extend(key, deadlineMillis)
    if redis.call('PEXPIRETIME', key) < deadlineMillis then
        redis.call('PEXPIREAT', key, deadlineMillis)
    end
end

local function positionOf(token)
    local rank = redis.call('ZRANK', waitingKey, token)
    if rank == false then
        return 0
    end
    return rank + 1
end

local function reply(created, token, status, lastPolledAt, lapsesRemaining, hardExpiresAt, admittedUntil, sequence)
    return {
        int(created),
        token,
        memberId,
        int(positionOf(token)),
        status,
        int(lastPolledAt),
        int(lapsesRemaining),
        int(hardExpiresAt),
        admittedUntil or '',
        int(sequence)
    }
end

local existing = redis.call('GET', memberKey)
if existing then
    local tokenKey = keyPrefix .. ':token:' .. existing
    local fields = redis.call('HMGET', tokenKey,
        'status', 'lastPolledAt', 'lapsesRemaining', 'hardExpiresAt', 'admittedUntil', 'sequence')

    if not fields[1] then
        forget(existing)
    elseif tonumber(fields[4]) < nowMillis then
        forget(existing)
    elseif fields[1] ~= 'WAITING' then
        return reply(0, existing, fields[1], tonumber(fields[2]), tonumber(fields[3]),
            tonumber(fields[4]), fields[5], tonumber(fields[6]))
    else
        local elapsed = nowMillis - tonumber(fields[2])
        local band = math.max(0, math.ceil(elapsed / graceMillis) - 1)
        local lapsesRemaining = math.max(0, tonumber(fields[3]) - band)
        local hardExpiresAt = nowMillis + hardExpiryMillis
        local deadline = hardExpiresAt + cleanupSlackMillis

        redis.call('HSET', tokenKey,
            'lastPolledAt', nowMillis,
            'lapsesRemaining', lapsesRemaining,
            'hardExpiresAt', hardExpiresAt)
        redis.call('ZADD', expiryKey, hardExpiresAt, existing)
        redis.call('PEXPIREAT', tokenKey, deadline)
        redis.call('PEXPIREAT', memberKey, deadline)
        extend(sequenceKey, deadline)
        extend(waitingKey, deadline)
        extend(expiryKey, deadline)

        return reply(0, existing, 'WAITING', nowMillis, lapsesRemaining, hardExpiresAt,
            fields[5], tonumber(fields[6]))
    end
end

local sequence = redis.call('INCR', sequenceKey)
local hardExpiresAt = nowMillis + hardExpiryMillis
local deadline = hardExpiresAt + cleanupSlackMillis
local tokenKey = keyPrefix .. ':token:' .. candidateToken

redis.call('ZADD', waitingKey, sequence, candidateToken)
redis.call('ZADD', expiryKey, hardExpiresAt, candidateToken)
redis.call('SET', memberKey, candidateToken)
redis.call('PEXPIREAT', memberKey, deadline)
redis.call('HSET', tokenKey,
    'memberId', memberId,
    'sequence', sequence,
    'status', 'WAITING',
    'lastPolledAt', nowMillis,
    'lapsesRemaining', maxLapses,
    'hardExpiresAt', hardExpiresAt)
redis.call('PEXPIREAT', tokenKey, deadline)
extend(sequenceKey, deadline)
extend(waitingKey, deadline)
extend(expiryKey, deadline)

return reply(1, candidateToken, 'WAITING', nowMillis, maxLapses, hardExpiresAt, '', sequence)
