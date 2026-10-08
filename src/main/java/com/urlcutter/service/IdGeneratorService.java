package com.urlcutter.service;

import com.urlcutter.util.Base62;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class IdGeneratorService {
    private static final String REDIS_COUNTER_KEY = "global_url_id_counter";
    private static final long RANGE_SIZE = 1000;

    private final StringRedisTemplate redisTemplate;
    private final AtomicLong currentId = new AtomicLong(0);
    private final AtomicLong maxId = new AtomicLong(0);

    public IdGeneratorService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public synchronized String generateShortCode() {
        if (currentId.get() >= maxId.get()) {
            fetchNextRange();
        }
        return Base62.encode(currentId.getAndIncrement());
    }

    private void fetchNextRange() {
        Long nextRangeEnd = redisTemplate.opsForValue().increment(REDIS_COUNTER_KEY, RANGE_SIZE);
        if (nextRangeEnd != null) {
            maxId.set(nextRangeEnd);
            currentId.set(nextRangeEnd - RANGE_SIZE + 1);
        }
    }
}