package com.ahmadda.infra.redis;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.ahmadda.infra.redis.EventParticipationStreamConfig.CONSUMER_GROUP;
import static com.ahmadda.infra.redis.EventParticipationStreamConfig.DLQ_STREAM_KEY;
import static com.ahmadda.infra.redis.EventParticipationStreamConfig.STREAM_KEY;

@Component
public class EventParticipationDeadLetterPublisher {

    private static final String PAYLOAD_FIELD = "payload";
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 500;
    private static final String DLQ_METRIC = "event.participation.stream.dlq";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> moveToDlqScript;
    private final MeterRegistry meterRegistry;

    public EventParticipationDeadLetterPublisher(
            final StringRedisTemplate redisTemplate,
            @Qualifier("moveEventParticipationToDlqScript")
            final DefaultRedisScript<Long> moveToDlqScript,
            final MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.moveToDlqScript = moveToDlqScript;
        this.meterRegistry = meterRegistry;
    }

    public boolean publish(
            final MapRecord<String, String, String> record,
            final long attempts,
            final Throwable failure
    ) {
        Long result = redisTemplate.execute(
                moveToDlqScript,
                List.of(STREAM_KEY, DLQ_STREAM_KEY),
                CONSUMER_GROUP,
                record.getId()
                        .getValue(),
                record.getValue()
                        .getOrDefault(PAYLOAD_FIELD, ""),
                Long.toString(attempts),
                failure.getClass()
                        .getName(),
                failureMessage(failure),
                LocalDateTime.now()
                        .toString()
        );

        if (result != null && result > 0) {
            meterRegistry.counter(DLQ_METRIC)
                    .increment();
            return true;
        }

        return false;
    }

    private String failureMessage(final Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass()
                    .getSimpleName();
        }
        if (message.length() <= MAX_FAILURE_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }
}
