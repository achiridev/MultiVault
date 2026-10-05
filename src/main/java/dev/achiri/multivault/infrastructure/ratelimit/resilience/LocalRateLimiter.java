package dev.achiri.multivault.infrastructure.ratelimit.resilience;

import dev.achiri.multivault.infrastructure.ratelimit.model.BucketSpec;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitDecision;
import dev.achiri.multivault.infrastructure.ratelimit.model.RateLimitScope;
import dev.achiri.multivault.infrastructure.ratelimit.spi.RateLimiter;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalRateLimiter implements RateLimiter {

    private static final Duration IDLE_RETENTION = Duration.ofMinutes(30);
    private static final int EVICTION_CHECK_INTERVAL_IN_CONSUMPTIONS = 32;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final Map<String, LocalBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger consumptionsSinceEviction = new AtomicInteger();
    private final TimeMeter timeMeter;
    private final Clock clock;

    public LocalRateLimiter() {
        this(Clock.systemUTC());
    }

    LocalRateLimiter(Clock clock) {
        this.clock = clock;
        this.timeMeter = new ClockTimeMeter(clock);
    }

    @Override
    public RateLimitDecision consume(String key, RateLimitScope scope, String ruleId, BucketSpec spec) {
        evictIdleBucketsIfDue();
        LocalBucket local = buckets.computeIfAbsent(
                ruleId + ':' + scope.name() + ':' + key, ignored -> new LocalBucket(newBucket(spec)));
        ConsumptionProbe probe = local.bucket().tryConsumeAndReturnRemaining(1);
        local.touch(clock.instant());
        if (probe.isConsumed()) {
            return RateLimitDecision.allowed(ruleId, scope, spec.capacity(), probe.getRemainingTokens());
        }
        return RateLimitDecision.rejected(
                ruleId, scope, spec.capacity(), Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket(BucketSpec spec) {
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(limit -> limit.capacity(spec.capacity())
                        .refillGreedy(spec.refillTokens(), spec.refillPeriod()))
                .build();
    }

    private void evictIdleBucketsIfDue() {
        if (consumptionsSinceEviction.incrementAndGet() < EVICTION_CHECK_INTERVAL_IN_CONSUMPTIONS) {
            return;
        }
        if (!consumptionsSinceEviction.compareAndSet(EVICTION_CHECK_INTERVAL_IN_CONSUMPTIONS, 0)) {
            return;
        }
        evictIdleBuckets();
    }

    private void evictIdleBuckets() {
        Instant cutoff = clock.instant().minus(IDLE_RETENTION);
        Iterator<Map.Entry<String, LocalBucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().lastUsed().isBefore(cutoff)) {
                iterator.remove();
            }
        }
    }

    int trackedBuckets() {
        return buckets.size();
    }

    private static final class ClockTimeMeter implements TimeMeter {

        private final Clock clock;

        private ClockTimeMeter(Clock clock) {
            this.clock = clock;
        }

        @Override
        public long currentTimeNanos() {
            return clock.millis() * NANOS_PER_MILLI;
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }

    private static final class LocalBucket {

        private final Bucket bucket;
        private volatile Instant lastUsed;

        private LocalBucket(Bucket bucket) {
            this.bucket = bucket;
            this.lastUsed = Instant.MIN;
        }

        private Bucket bucket() {
            return bucket;
        }

        private Instant lastUsed() {
            return lastUsed;
        }

        private void touch(Instant instant) {
            lastUsed = instant;
        }
    }
}