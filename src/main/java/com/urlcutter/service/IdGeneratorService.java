package com.urlcutter.service;

import com.urlcutter.util.Base62;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
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
        if (currentId.get() >= maxId.get()) {
            throw new IllegalStateException("Unable to allocate a short URL ID from Redis");
        }
        return Base62.encode(currentId.getAndIncrement());
    }

    private void fetchNextRange() {
        final Long nextRangeEnd;
        try {
            nextRangeEnd = redisTemplate.opsForValue().increment(REDIS_COUNTER_KEY, RANGE_SIZE);
        } catch (DataAccessException exception) {
            throw new IllegalStateException("Unable to allocate a short URL ID from Redis", exception);
        }
        if (nextRangeEnd == null || nextRangeEnd < RANGE_SIZE) {
            throw new IllegalStateException("Redis returned an invalid short URL ID range");
        }
        maxId.set(nextRangeEnd + 1);
        currentId.set(nextRangeEnd - RANGE_SIZE + 1);
    }
}
