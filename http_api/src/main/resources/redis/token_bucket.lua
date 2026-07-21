-- Atomic true token bucket (ADR-0041/ADR-0042), behaviourally equivalent to
-- LocalTokenBucketRateLimiter: continuous refill, no fixed-window reset.
--
-- KEYS[1] = bucket key (e.g. "ratelimit:auth:203.0.113.9")
-- ARGV[1] = capacity (limit, whole tokens)
-- ARGV[2] = refill period in millis (time to refill the whole bucket)
-- ARGV[3] = requested tokens
--
-- Returns { allowed(0|1), remaining, limit, refillAtEpochMillis, waitMillis }.
-- The Redis server clock (TIME) is authoritative, so per-pod clock skew cannot desync buckets.

local cap    = tonumber(ARGV[1])
local period = tonumber(ARGV[2])
local req    = tonumber(ARGV[3])

local t   = redis.call('TIME')
local now = t[1] * 1000 + math.floor(t[2] / 1000)

local data   = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts     = tonumber(data[2])
if tokens == nil then
  tokens = cap
  ts = now
end

local elapsed = now - ts
if elapsed < 0 then elapsed = 0 end
tokens = math.min(cap, tokens + elapsed * cap / period)

local allowed = 0
local wait = 0
if tokens >= req then
  allowed = 1
  tokens = tokens - req
else
  wait = math.ceil((req - tokens) * period / cap)
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
redis.call('PEXPIRE', KEYS[1], period * 2)

local remaining    = math.floor(tokens)
local millis_full  = math.ceil((cap - tokens) * period / cap)
local refill_at    = now + millis_full

return { allowed, remaining, cap, refill_at, wait }
