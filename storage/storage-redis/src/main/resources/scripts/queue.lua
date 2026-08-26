#!lua name=queue

local function int(value)
    return string.format('%d', value)
end

local function globalMember(keyPrefix, token)
    return keyPrefix .. '|' .. token
end

local function extend(key, deadlineMillis)
    local current = redis.call('PEXPIRETIME', key)
    if current < deadlineMillis then
        redis.call('PEXPIREAT', key, deadlineMillis)
    end
end

local function removeOwner(keyPrefix, tokenKey, token)
    local owner = redis.call('HGET', tokenKey, 'memberId')
    if owner then
        local ownerKey = keyPrefix .. ':member:' .. owner
        if redis.call('GET', ownerKey) == token then
            redis.call('DEL', ownerKey)
        end
    end
end

local function forgetWaiting(keyPrefix, waitingKey, admissionWaitingKey, expiryKey, token)
    local tokenKey = keyPrefix .. ':token:' .. token
    removeOwner(keyPrefix, tokenKey, token)
    redis.call('DEL', tokenKey)
    redis.call('ZREM', waitingKey, token)
    redis.call('ZREM', admissionWaitingKey, token)
    redis.call('ZREM', expiryKey, token)
end

local function expireAdmitted(keyPrefix, expiryKey, admittedKey, globalAdmittedKey, token)
    local tokenKey = keyPrefix .. ':token:' .. token
    removeOwner(keyPrefix, tokenKey, token)
    if redis.call('EXISTS', tokenKey) == 1 then
        redis.call('HSET', tokenKey, 'status', 'EXPIRED')
    end
    redis.call('ZREM', expiryKey, token)
    redis.call('ZREM', admittedKey, token)
    redis.call('ZREM', globalAdmittedKey, globalMember(keyPrefix, token))
end

local function purgeSchedule(keyPrefix, waitingKey, admissionWaitingKey, expiryKey, admittedKey,
                            globalAdmittedKey, nowMillis, limit)
    local stale = redis.call('ZRANGEBYSCORE', expiryKey, '-inf', nowMillis, 'LIMIT', 0, limit)
    local purged = 0
    for index = 1, #stale do
        local token = stale[index]
        local tokenKey = keyPrefix .. ':token:' .. token
        local fields = redis.call('HMGET', tokenKey, 'status', 'hardExpiresAt')
        local status = fields[1]
        if status == 'ADMITTED' then
            expireAdmitted(keyPrefix, expiryKey, admittedKey, globalAdmittedKey, token)
            purged = purged + 1
        elseif not fields[2] or tonumber(fields[2]) < nowMillis then
            forgetWaiting(keyPrefix, waitingKey, admissionWaitingKey, expiryKey, token)
            purged = purged + 1
        end
    end
    return purged
end

local function purgeGlobalAdmitted(globalAdmittedKey, nowMillis)
    local stale = redis.call('ZRANGEBYSCORE', globalAdmittedKey, '-inf', nowMillis)
    for index = 1, #stale do
        local keyPrefix, token = string.match(stale[index], '^(.-)|(.+)$')
        if keyPrefix and token then
            expireAdmitted(keyPrefix, keyPrefix .. ':expiry', keyPrefix .. ':admitted',
                globalAdmittedKey, token)
        else
            redis.call('ZREM', globalAdmittedKey, stale[index])
        end
    end
end

local function positionOf(waitingKey, token)
    local rank = redis.call('ZRANK', waitingKey, token)
    if rank == false then
        return 0
    end
    return rank + 1
end

local function tokenReply(token, memberId, position, status, lastPolledAt,
                          lapsesRemaining, sequence, admittedUntil)
    local reply = {
        'token', token,
        'memberId', memberId,
        'position', int(position),
        'status', status,
        'lastPolledAt', int(lastPolledAt),
        'lapsesRemaining', int(lapsesRemaining),
        'sequence', int(sequence)
    }
    if admittedUntil then
        table.insert(reply, 'admittedUntil')
        table.insert(reply, int(admittedUntil))
    end
    return reply
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

local function refreshWaiting(sequenceKey, waitingKey, admissionWaitingKey, expiryKey,
                              memberKey, tokenKey, token, sequence, nowMillis,
                              lapsesRemaining, hardExpiresAt,
                              cleanupSlackMillis)
    local deadline = hardExpiresAt + cleanupSlackMillis
    redis.call('HSET', tokenKey,
        'lastPolledAt', nowMillis,
        'lapsesRemaining', lapsesRemaining,
        'hardExpiresAt', hardExpiresAt)
    redis.call('ZADD', expiryKey, hardExpiresAt, token)
    redis.call('ZADD', admissionWaitingKey, sequence, token)
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
    local admittedKey = keys[5]
    local globalAdmittedKey = keys[6]
    local admissionWaitingKey = keys[7]
    local admissionSchedulesKey = keys[8]

    local nowMillis = tonumber(args[1])
    local candidateToken = args[2]
    local memberId = args[3]
    local keyPrefix = args[4]
    local graceMillis = tonumber(args[5])
    local maxLapses = tonumber(args[6])
    local hardExpiryMillis = tonumber(args[7])
    local cleanupSlackMillis = tonumber(args[8])
    local purgeLimit = tonumber(args[9])
    local scheduleId = args[10]

    purgeSchedule(keyPrefix, waitingKey, admissionWaitingKey, expiryKey, admittedKey,
        globalAdmittedKey, nowMillis, purgeLimit)

    local existing = redis.call('GET', memberKey)
    if existing then
        local tokenKey = keyPrefix .. ':token:' .. existing
        local fields = redis.call('HMGET', tokenKey,
            'status', 'lastPolledAt', 'lapsesRemaining', 'hardExpiresAt',
            'sequence', 'admittedUntil')

        if not fields[1] then
            redis.call('DEL', memberKey)
        elseif fields[1] == 'ADMITTED' and tonumber(fields[6]) > nowMillis then
            return append(tokenReply(existing, memberId, 0, 'ADMITTED',
                tonumber(fields[2]), tonumber(fields[3]), tonumber(fields[5]),
                tonumber(fields[6])), 'created', '0')
        elseif fields[1] == 'WAITING' and tonumber(fields[4]) >= nowMillis then
            local lapsesRemaining = remainingLapses(
                nowMillis - tonumber(fields[2]), graceMillis, tonumber(fields[3]))
            local hardExpiresAt = nowMillis + hardExpiryMillis

            refreshWaiting(sequenceKey, waitingKey, admissionWaitingKey, expiryKey,
                memberKey, tokenKey, existing, tonumber(fields[5]), nowMillis,
                lapsesRemaining, hardExpiresAt, cleanupSlackMillis)
            redis.call('ZADD', admissionSchedulesKey, 'NX', nowMillis, scheduleId)

            return append(tokenReply(existing, memberId, positionOf(waitingKey, existing),
                'WAITING', nowMillis, lapsesRemaining, tonumber(fields[5]), nil),
                'created', '0')
        else
            if fields[1] == 'ADMITTED' then
                expireAdmitted(keyPrefix, expiryKey, admittedKey, globalAdmittedKey, existing)
            else
                forgetWaiting(keyPrefix, waitingKey, admissionWaitingKey, expiryKey, existing)
            end
        end
    end

    local sequence = redis.call('INCR', sequenceKey)
    local hardExpiresAt = nowMillis + hardExpiryMillis
    local deadline = hardExpiresAt + cleanupSlackMillis
    local tokenKey = keyPrefix .. ':token:' .. candidateToken

    redis.call('ZADD', waitingKey, sequence, candidateToken)
    redis.call('ZADD', admissionWaitingKey, sequence, candidateToken)
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
    redis.call('ZADD', admissionSchedulesKey, 'NX', nowMillis, scheduleId)

    return append(tokenReply(candidateToken, memberId, positionOf(waitingKey, candidateToken),
        'WAITING', nowMillis, maxLapses, sequence, nil), 'created', '1')
end

local function record_poll(keys, args)
    local sequenceKey = keys[1]
    local waitingKey = keys[2]
    local expiryKey = keys[3]
    local tokenKey = keys[4]
    local memberKey = keys[5]
    local admittedKey = keys[6]
    local globalAdmittedKey = keys[7]
    local admissionWaitingKey = keys[8]
    local admissionSchedulesKey = keys[9]

    local nowMillis = tonumber(args[1])
    local queueToken = args[2]
    local memberId = args[3]
    local keyPrefix = args[4]
    local graceMillis = tonumber(args[5])
    local hardExpiryMillis = tonumber(args[6])
    local cleanupSlackMillis = tonumber(args[7])
    local purgeLimit = tonumber(args[8])
    local scheduleId = args[9]

    local fields = redis.call('HMGET', tokenKey,
        'memberId', 'status', 'lastPolledAt', 'lapsesRemaining',
        'hardExpiresAt', 'sequence', 'admittedUntil')

    if not fields[1] then
        return { 'outcome', 'NOT_FOUND' }
    end
    if fields[1] ~= memberId then
        return { 'outcome', 'NOT_OWNED' }
    end
    if fields[2] == 'EXPIRED' then
        return { 'outcome', 'EXPIRED' }
    end
    if fields[2] == 'ADMITTED' then
        if tonumber(fields[7]) <= nowMillis then
            expireAdmitted(keyPrefix, expiryKey, admittedKey, globalAdmittedKey, queueToken)
            return { 'outcome', 'EXPIRED' }
        end
        return append(tokenReply(queueToken, memberId, 0, 'ADMITTED',
            tonumber(fields[3]), tonumber(fields[4]), tonumber(fields[6]),
            tonumber(fields[7])), 'outcome', 'UPDATED')
    end
    if tonumber(fields[5]) < nowMillis then
        forgetWaiting(keyPrefix, waitingKey, admissionWaitingKey, expiryKey, queueToken)
        return { 'outcome', 'EXPIRED' }
    end

    purgeSchedule(keyPrefix, waitingKey, admissionWaitingKey, expiryKey, admittedKey,
        globalAdmittedKey, nowMillis, purgeLimit)

    local lapsesRemaining = remainingLapses(
        nowMillis - tonumber(fields[3]), graceMillis, tonumber(fields[4]))
    local hardExpiresAt = nowMillis + hardExpiryMillis

    refreshWaiting(sequenceKey, waitingKey, admissionWaitingKey, expiryKey,
        memberKey, tokenKey, queueToken, tonumber(fields[6]), nowMillis,
        lapsesRemaining, hardExpiresAt, cleanupSlackMillis)
    redis.call('ZADD', admissionSchedulesKey, 'NX', nowMillis, scheduleId)

    return append(tokenReply(queueToken, memberId, positionOf(waitingKey, queueToken),
        'WAITING', nowMillis, lapsesRemaining, tonumber(fields[6]), nil),
        'outcome', 'UPDATED')
end

local function authorize_and_renew(keys, args)
    local tokenKey = keys[1]
    local admittedKey = keys[2]
    local globalAdmittedKey = keys[3]
    local expiryKey = keys[4]

    local nowMillis = tonumber(args[1])
    local queueToken = args[2]
    local memberId = args[3]
    local keyPrefix = args[4]
    local renewalWindowMillis = tonumber(args[5])

    local fields = redis.call('HMGET', tokenKey,
        'memberId', 'status', 'admittedUntil', 'admissionHardExpiresAt')

    if not fields[1] then
        return { 'outcome', 'NOT_FOUND' }
    end
    if fields[1] ~= memberId then
        return { 'outcome', 'NOT_OWNED' }
    end
    if fields[2] == 'EXPIRED' then
        return { 'outcome', 'EXPIRED' }
    end
    if fields[2] ~= 'ADMITTED' then
        return { 'outcome', 'NOT_ADMITTED' }
    end

    local admittedUntil = tonumber(fields[3])
    local admissionHardExpiresAt = tonumber(fields[4])
    if not admittedUntil or not admissionHardExpiresAt
            or admittedUntil <= nowMillis or admissionHardExpiresAt <= nowMillis then
        expireAdmitted(keyPrefix, expiryKey, admittedKey, globalAdmittedKey, queueToken)
        return { 'outcome', 'EXPIRED' }
    end

    local renewedUntil = math.min(nowMillis + renewalWindowMillis, admissionHardExpiresAt)
    redis.call('HSET', tokenKey, 'admittedUntil', renewedUntil)
    redis.call('ZADD', admittedKey, renewedUntil, queueToken)
    redis.call('ZADD', globalAdmittedKey, renewedUntil, globalMember(keyPrefix, queueToken))
    redis.call('ZADD', expiryKey, renewedUntil, queueToken)

    return {
        'outcome', 'AUTHORIZED',
        'renewedUntil', int(renewedUntil)
    }
end

local function nextEligible(keyPrefix, admissionWaitingKey, cutoff, scanLimit)
    local candidates = redis.call('ZRANGE', admissionWaitingKey, 0, scanLimit - 1)
    for index = 1, #candidates do
        local token = candidates[index]
        local fields = redis.call('HMGET', keyPrefix .. ':token:' .. token, 'status', 'lastPolledAt')
        if not fields[1] then
            redis.call('ZREM', admissionWaitingKey, token)
        elseif fields[1] == 'WAITING' and tonumber(fields[2]) >= cutoff then
            return token
        else
            redis.call('ZREM', admissionWaitingKey, token)
        end
    end
    return nil
end

local function promote(keyPrefix, token, nowMillis, initialLeaseMillis,
                       hardCapMillis, cleanupSlackMillis, globalAdmittedKey)
    local tokenKey = keyPrefix .. ':token:' .. token
    local fields = redis.call('HMGET', tokenKey, 'memberId')
    if not fields[1] then
        redis.call('ZREM', keyPrefix .. ':waiting', token)
        return false
    end

    local admittedUntil = nowMillis + initialLeaseMillis
    local hardExpiresAt = nowMillis + hardCapMillis
    local deadline = hardExpiresAt + cleanupSlackMillis
    local memberKey = keyPrefix .. ':member:' .. fields[1]

    redis.call('HSET', tokenKey,
        'status', 'ADMITTED',
        'admittedAt', nowMillis,
        'admittedUntil', admittedUntil,
        'admissionHardExpiresAt', hardExpiresAt,
        'hardExpiresAt', hardExpiresAt)
    redis.call('ZREM', keyPrefix .. ':waiting', token)
    redis.call('ZREM', keyPrefix .. ':admission-waiting', token)
    redis.call('ZADD', keyPrefix .. ':admitted', admittedUntil, token)
    redis.call('ZADD', globalAdmittedKey, admittedUntil, globalMember(keyPrefix, token))
    redis.call('ZADD', keyPrefix .. ':expiry', admittedUntil, token)
    redis.call('PEXPIREAT', tokenKey, deadline)
    redis.call('PEXPIREAT', memberKey, deadline)
    extend(keyPrefix .. ':admitted', deadline)
    extend(keyPrefix .. ':expiry', deadline)
    extend(globalAdmittedKey, deadline)
    return true
end

local function orderedSchedules(scheduleKey, cursorKey, limit)
    local size = redis.call('ZCARD', scheduleKey)
    if size == 0 then
        return {}
    end
    local cursor = redis.call('GET', cursorKey)
    local start = 0
    if cursor then
        local rank = redis.call('ZRANK', scheduleKey, cursor)
        if rank then
            start = (rank + 1) % size
        end
    end
    local count = math.min(limit, size)
    local schedules = redis.call('ZRANGE', scheduleKey, start, math.min(size - 1, start + count - 1))
    if #schedules < count then
        local wrapped = redis.call('ZRANGE', scheduleKey, 0, count - #schedules - 1)
        for index = 1, #wrapped do
            table.insert(schedules, wrapped[index])
        end
    end
    return schedules
end

local function admit(keys, args)
    local scheduleKey = keys[1]
    local cursorKey = keys[2]
    local globalAdmittedKey = keys[3]

    local nowMillis = tonumber(args[1])
    local rootPrefix = args[2]
    local activityWindowMillis = tonumber(args[3])
    local initialLeaseMillis = tonumber(args[4])
    local hardCapMillis = tonumber(args[5])
    local perScheduleCapacity = tonumber(args[6])
    local globalCapacity = tonumber(args[7])
    local maxAdmissions = tonumber(args[8])
    local scanLimit = tonumber(args[9])
    local scheduleScanLimit = tonumber(args[10])
    local cleanupSlackMillis = tonumber(args[11])

    purgeGlobalAdmitted(globalAdmittedKey, nowMillis)
    local schedules = orderedSchedules(scheduleKey, cursorKey, scheduleScanLimit)
    local admitted = 0
    local progress = true

    while progress and admitted < maxAdmissions
            and redis.call('ZCARD', globalAdmittedKey) < globalCapacity do
        progress = false
        for index = 1, #schedules do
            if admitted >= maxAdmissions
                    or redis.call('ZCARD', globalAdmittedKey) >= globalCapacity then
                break
            end

            local scheduleId = schedules[index]
            local keyPrefix = rootPrefix .. ':{' .. scheduleId .. '}'
            local waitingKey = keyPrefix .. ':waiting'
            local admissionWaitingKey = keyPrefix .. ':admission-waiting'
            local expiryKey = keyPrefix .. ':expiry'
            local admittedKey = keyPrefix .. ':admitted'
            redis.call('SET', cursorKey, scheduleId)

            purgeSchedule(keyPrefix, waitingKey, admissionWaitingKey, expiryKey, admittedKey,
                globalAdmittedKey, nowMillis, scanLimit)

            if redis.call('ZCARD', admittedKey) < perScheduleCapacity then
                local token = nextEligible(
                    keyPrefix, admissionWaitingKey, nowMillis - activityWindowMillis, scanLimit)
                if token and promote(keyPrefix, token, nowMillis, initialLeaseMillis,
                        hardCapMillis, cleanupSlackMillis, globalAdmittedKey) then
                    admitted = admitted + 1
                    progress = true
                end
            end

            if redis.call('ZCARD', admissionWaitingKey) == 0 then
                redis.call('ZREM', scheduleKey, scheduleId)
            end
        end
    end

    return {
        'admitted', int(admitted),
        'active', int(redis.call('ZCARD', globalAdmittedKey))
    }
end

local function sweep_expired(keys, args)
    local waitingKey = keys[1]
    local expiryKey = keys[2]
    local admittedKey = keys[3]
    local globalAdmittedKey = keys[4]
    local admissionWaitingKey = keys[5]

    local nowMillis = tonumber(args[1])
    local keyPrefix = args[2]
    local batchSize = tonumber(args[3])

    local purged = purgeSchedule(keyPrefix, waitingKey, admissionWaitingKey, expiryKey, admittedKey,
        globalAdmittedKey, nowMillis, batchSize)

    return {
        'purged', int(purged),
        'remaining', int(redis.call('ZCOUNT', expiryKey, '-inf', nowMillis)),
        'alive', int(redis.call('ZCARD', waitingKey) + redis.call('ZCARD', admittedKey))
    }
end

local function release_admission_lease(keys, args)
    if redis.call('GET', keys[1]) == args[1] then
        redis.call('DEL', keys[1])
        return { 'released', '1' }
    end
    return { 'released', '0' }
end

redis.register_function('queue_enter_or_resume', enter_or_resume)
redis.register_function('queue_record_poll', record_poll)
redis.register_function('queue_authorize_and_renew', authorize_and_renew)
redis.register_function('queue_admit', admit)
redis.register_function('queue_sweep_expired', sweep_expired)
redis.register_function('queue_release_admission_lease', release_admission_lease)
