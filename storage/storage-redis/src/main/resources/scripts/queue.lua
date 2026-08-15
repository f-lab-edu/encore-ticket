#!lua name=queue

local function int(value)
    return string.format('%d', value)
end

local function forget(keyPrefix, waitingKey, expiryKey, token)
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

local function purge(keyPrefix, waitingKey, expiryKey, nowMillis, limit)
    local stale = redis.call('ZRANGEBYSCORE', expiryKey, '-inf', '(' .. int(nowMillis),
        'LIMIT', 0, limit)
    for index = 1, #stale do
        forget(keyPrefix, waitingKey, expiryKey, stale[index])
    end
    return #stale
end

local function extend(key, deadlineMillis)
    if redis.call('PEXPIRETIME', key) < deadlineMillis then
        redis.call('PEXPIREAT', key, deadlineMillis)
    end
end

local function positionOf(waitingKey, token)
    local rank = redis.call('ZRANK', waitingKey, token)
    if rank == false then
        return 0
    end
    return rank + 1
end

local function tokenReply(token, memberId, position, lastPolledAt, lapsesRemaining, sequence)
    return {
        'token', token,
        'memberId', memberId,
        'position', int(position),
        'status', 'WAITING',
        'lastPolledAt', int(lastPolledAt),
        'lapsesRemaining', int(lapsesRemaining),
        'sequence', int(sequence)
    }
end

local function append(reply, name, value)
    table.insert(reply, name)
    table.insert(reply, value)
    return reply
end

local function remainingLapses(elapsed, graceMillis, current)
    local band = math.max(0, math.ceil(elapsed / graceMillis) - 1)
    return math.max(0, current - band)
end

local function refresh(sequenceKey, waitingKey, expiryKey, memberKey, tokenKey,
                      token, nowMillis, lapsesRemaining, hardExpiresAt, cleanupSlackMillis)
    local deadline = hardExpiresAt + cleanupSlackMillis
    redis.call('HSET', tokenKey,
        'lastPolledAt', nowMillis,
        'lapsesRemaining', lapsesRemaining,
        'hardExpiresAt', hardExpiresAt)
    redis.call('ZADD', expiryKey, hardExpiresAt, token)
    redis.call('PEXPIREAT', tokenKey, deadline)
    redis.call('PEXPIREAT', memberKey, deadline)
    extend(sequenceKey, deadline)
    extend(waitingKey, deadline)
    extend(expiryKey, deadline)
end

local function enter_or_resume(keys, args)
    local sequenceKey = keys[1]
    local waitingKey = keys[2]
    local expiryKey = keys[3]
    local memberKey = keys[4]

    local nowMillis = tonumber(args[1])
    local candidateToken = args[2]
    local memberId = args[3]
    local keyPrefix = args[4]
    local graceMillis = tonumber(args[5])
    local maxLapses = tonumber(args[6])
    local hardExpiryMillis = tonumber(args[7])
    local cleanupSlackMillis = tonumber(args[8])
    local purgeLimit = tonumber(args[9])

    purge(keyPrefix, waitingKey, expiryKey, nowMillis, purgeLimit)

    local existing = redis.call('GET', memberKey)
    if existing then
        local tokenKey = keyPrefix .. ':token:' .. existing
        local fields = redis.call('HMGET', tokenKey,
            'status', 'lastPolledAt', 'lapsesRemaining', 'hardExpiresAt', 'sequence')

        if not fields[1] then
            forget(keyPrefix, waitingKey, expiryKey, existing)
        elseif tonumber(fields[4]) < nowMillis then
            forget(keyPrefix, waitingKey, expiryKey, existing)
        elseif fields[1] ~= 'WAITING' then
            return redis.error_reply('ADMITTED_NOT_SUPPORTED')
        else
            local lapsesRemaining = remainingLapses(
                nowMillis - tonumber(fields[2]), graceMillis, tonumber(fields[3]))
            local hardExpiresAt = nowMillis + hardExpiryMillis

            refresh(sequenceKey, waitingKey, expiryKey, memberKey, tokenKey,
                existing, nowMillis, lapsesRemaining, hardExpiresAt, cleanupSlackMillis)

            return append(tokenReply(existing, memberId, positionOf(waitingKey, existing),
                nowMillis, lapsesRemaining, tonumber(fields[5])), 'created', '0')
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

    return append(tokenReply(candidateToken, memberId, positionOf(waitingKey, candidateToken),
        nowMillis, maxLapses, sequence), 'created', '1')
end

local function record_poll(keys, args)
    local sequenceKey = keys[1]
    local waitingKey = keys[2]
    local expiryKey = keys[3]
    local tokenKey = keys[4]
    local memberKey = keys[5]

    local nowMillis = tonumber(args[1])
    local queueToken = args[2]
    local memberId = args[3]
    local keyPrefix = args[4]
    local graceMillis = tonumber(args[5])
    local hardExpiryMillis = tonumber(args[6])
    local cleanupSlackMillis = tonumber(args[7])
    local purgeLimit = tonumber(args[8])

    local fields = redis.call('HMGET', tokenKey,
        'memberId', 'status', 'lastPolledAt', 'lapsesRemaining', 'hardExpiresAt', 'sequence')

    if not fields[1] then
        return { 'outcome', 'NOT_FOUND' }
    end
    if fields[1] ~= memberId then
        return { 'outcome', 'NOT_OWNED' }
    end
    if tonumber(fields[5]) < nowMillis then
        forget(keyPrefix, waitingKey, expiryKey, queueToken)
        return { 'outcome', 'EXPIRED' }
    end
    if fields[2] ~= 'WAITING' then
        return redis.error_reply('ADMITTED_NOT_SUPPORTED')
    end

    purge(keyPrefix, waitingKey, expiryKey, nowMillis, purgeLimit)

    local lapsesRemaining = remainingLapses(
        nowMillis - tonumber(fields[3]), graceMillis, tonumber(fields[4]))
    local hardExpiresAt = nowMillis + hardExpiryMillis

    refresh(sequenceKey, waitingKey, expiryKey, memberKey, tokenKey,
        queueToken, nowMillis, lapsesRemaining, hardExpiresAt, cleanupSlackMillis)

    return append(tokenReply(queueToken, memberId, positionOf(waitingKey, queueToken),
        nowMillis, lapsesRemaining, tonumber(fields[6])), 'outcome', 'UPDATED')
end

local function sweep_expired(keys, args)
    local waitingKey = keys[1]
    local expiryKey = keys[2]

    local nowMillis = tonumber(args[1])
    local keyPrefix = args[2]
    local batchSize = tonumber(args[3])

    local purged = purge(keyPrefix, waitingKey, expiryKey, nowMillis, batchSize)

    return {
        'purged', int(purged),
        'remaining', int(redis.call('ZCOUNT', expiryKey, '-inf', '(' .. int(nowMillis))),
        'alive', int(redis.call('ZCARD', waitingKey))
    }
end

redis.register_function('queue_enter_or_resume', enter_or_resume)
redis.register_function('queue_record_poll', record_poll)
redis.register_function('queue_sweep_expired', sweep_expired)
