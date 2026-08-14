local sequenceKey = KEYS[1]
local waitingKey = KEYS[2]
local expiryKey = KEYS[3]
local tokenKey = KEYS[4]
local memberKey = KEYS[5]

local nowMillis = tonumber(ARGV[1])
local queueToken = ARGV[2]
local memberId = ARGV[3]
local keyPrefix = ARGV[4]
local graceMillis = tonumber(ARGV[5])
local hardExpiryMillis = tonumber(ARGV[6])
local cleanupSlackMillis = tonumber(ARGV[7])

local function int(value)
    return string.format('%d', value)
end

local function forget(token)
    local key = keyPrefix .. ':token:' .. token
    local owner = redis.call('HGET', key, 'memberId')
    if owner then
        local ownerKey = keyPrefix .. ':member:' .. owner
        if redis.call('GET', ownerKey) == token then
            redis.call('DEL', ownerKey)
        end
    end
    redis.call('DEL', key)
    redis.call('ZREM', waitingKey, token)
    redis.call('ZREM', expiryKey, token)
end

local fields = redis.call('HMGET', tokenKey,
    'memberId', 'status', 'lastPolledAt', 'lapsesRemaining', 'hardExpiresAt', 'admittedUntil', 'sequence')

if not fields[1] then
    return { 'NOT_FOUND' }
end
if fields[1] ~= memberId then
    return { 'NOT_OWNED' }
end
if tonumber(fields[5]) < nowMillis then
    forget(queueToken)
    return { 'EXPIRED' }
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

local function reply(status, lastPolledAt, lapsesRemaining, hardExpiresAt, admittedUntil)
    return {
        'UPDATED',
        queueToken,
        memberId,
        int(positionOf(queueToken)),
        status,
        int(lastPolledAt),
        int(lapsesRemaining),
        int(hardExpiresAt),
        admittedUntil or '',
        int(tonumber(fields[7]))
    }
end

if fields[2] ~= 'WAITING' then
    return reply(fields[2], tonumber(fields[3]), tonumber(fields[4]), tonumber(fields[5]), fields[6])
end

local elapsed = nowMillis - tonumber(fields[3])
local band = math.max(0, math.ceil(elapsed / graceMillis) - 1)
local lapsesRemaining = math.max(0, tonumber(fields[4]) - band)
local hardExpiresAt = nowMillis + hardExpiryMillis
local deadline = hardExpiresAt + cleanupSlackMillis

redis.call('HSET', tokenKey,
    'lastPolledAt', nowMillis,
    'lapsesRemaining', lapsesRemaining,
    'hardExpiresAt', hardExpiresAt)
redis.call('ZADD', expiryKey, hardExpiresAt, queueToken)
redis.call('PEXPIREAT', tokenKey, deadline)
redis.call('PEXPIREAT', memberKey, deadline)
extend(sequenceKey, deadline)
extend(waitingKey, deadline)
extend(expiryKey, deadline)

return reply('WAITING', nowMillis, lapsesRemaining, hardExpiresAt, fields[6])
