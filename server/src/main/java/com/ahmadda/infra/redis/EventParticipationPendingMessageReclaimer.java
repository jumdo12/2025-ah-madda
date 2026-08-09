package com.ahmadda.infra.redis;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static com.ahmadda.infra.redis.EventParticipationStreamConfig.CONSUMER_GROUP;
import static com.ahmadda.infra.redis.EventParticipationStreamConfig.MAX_DELIVERY_ATTEMPTS;
import static com.ahmadda.infra.redis.EventParticipationStreamConfig.STREAM_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "event-participation.stream.consumer.enabled",
        havingValue = "true"
)
public class EventParticipationPendingMessageReclaimer {

    private static final int PENDING_SCAN_COUNT = 1_000;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(5);
    private static final String RETRY_METRIC = "event.participation.stream.retry";
    private static final String RECOVERY_CONSUMER_PREFIX = "event-participation-reclaimer-";

    private final StringRedisTemplate redisTemplate;
    private final RedisEventParticipationStreamConsumer streamConsumer;
    private final MeterRegistry meterRegistry;
    private final String recoveryConsumerName = RECOVERY_CONSUMER_PREFIX + UUID.randomUUID();

    @Scheduled(fixedDelay = 1_000)
    public void reclaimPendingMessages() {
        try {
            StreamOperations<String, String, String> streamOperations = redisTemplate.opsForStream();
            PendingMessages pendingMessages = streamOperations
                    .pending(STREAM_KEY, CONSUMER_GROUP, Range.unbounded(), PENDING_SCAN_COUNT);

            for (PendingMessage pendingMessage : pendingMessages) {
                reclaimIfReady(streamOperations, pendingMessage);
            }
        } catch (DataAccessException exception) {
            log.warn("이벤트 신청 Pending 메시지 조회 실패", exception);
        } catch (RuntimeException exception) {
            log.error("이벤트 신청 Pending 메시지 회수 중 예상하지 못한 오류 발생", exception);
        }
    }

    private void reclaimIfReady(
            final StreamOperations<String, String, String> streamOperations,
            final PendingMessage pendingMessage
    ) {
        Duration retryDelay = retryDelay(pendingMessage.getTotalDeliveryCount());
        if (pendingMessage.getElapsedTimeSinceLastDelivery()
                .compareTo(retryDelay) < 0) {
            return;
        }

        List<MapRecord<String, String, String>> claimedRecords = streamOperations
                .claim(
                        STREAM_KEY,
                        CONSUMER_GROUP,
                        recoveryConsumerName,
                        retryDelay,
                        pendingMessage.getId()
                );
        if (claimedRecords.isEmpty()) {
            return;
        }

        MapRecord<String, String, String> record = claimedRecords.getFirst();
        long previousDeliveryCount = pendingMessage.getTotalDeliveryCount();
        if (previousDeliveryCount >= MAX_DELIVERY_ATTEMPTS) {
            streamConsumer.moveToDlq(
                    record,
                    previousDeliveryCount,
                    new IllegalStateException("이벤트 신청 메시지의 최대 재시도 횟수를 초과했습니다.")
            );
            return;
        }

        meterRegistry.counter(RETRY_METRIC)
                .increment();
        streamConsumer.process(record, previousDeliveryCount + 1);
    }

    private Duration retryDelay(final long deliveryCount) {
        int exponent = (int) Math.min(Math.max(deliveryCount - 1, 0), 3);
        return INITIAL_RETRY_DELAY.multipliedBy(1L << exponent);
    }
}
