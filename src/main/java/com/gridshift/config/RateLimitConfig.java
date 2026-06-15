package com.gridshift.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiter using Bucket4j token bucket algorithm.
 *
 * Each IP gets its own bucket: 30 requests/minute with a burst of 10.
 * Prevents a single client from burning through the ElectricityMaps
 * API quota or running up cloud costs.
 *
 * @author Saamarth Attray
 */
@Component
public class RateLimitConfig {

    @Value("${gridshift.rate-limit.requests-per-minute:30}")
    private int requestsPerMinute;

    @Value("${gridshift.rate-limit.burst-capacity:10}")
    private int burstCapacity;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String ip) {
        return buckets.computeIfAbsent(ip, this::newBucket).tryConsume(1);
    }

    private Bucket newBucket(String ip) {
        long capacity = Math.max(requestsPerMinute, burstCapacity);
        Bandwidth limit = Bandwidth.builder()
            .capacity(capacity)
            .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
            .initialTokens(Math.min(burstCapacity, capacity))
            .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
