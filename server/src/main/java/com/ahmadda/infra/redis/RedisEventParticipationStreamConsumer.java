package com.ahmadda.infra.redis;

import com.ahmadda.application.EventParticipationTransactionService;
import com.ahmadda.application.dto.EventParticipateRequest;
import com.ahmadda.application.dto.EventParticipationMessage;
import com.ahmadda.application.dto.LoginMember;
import com.ahmadda.domain.event.GuestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

import static com.ahmadda.infra.redis.EventParticipationStreamConfig.CONSUMER_GROUP;
import static com.ahmadda.infra.redis.EventParticipationStreamConfig.DATABASE_WRITER_CONCURRENCY;
import static com.ahmadda.infra.redis.EventParticipationStreamConfig.MAX_DELIVERY_ATTEMPTS;
import static com.ahmadda.infra.redis.EventParticipationStreamConfig.STREAM_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEventParticipationStreamConsumer
        implements StreamListener<String, MapRecord<String, String, String>> {

    private static final String PAYLOAD_FIELD = "payload";
    private static final String PROCESSED_METRIC = "event.participation.stream.processed";
    private static final String FAILURE_METRIC = "event.participation.stream.processing.failure";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final GuestRepository guestRepository;
    private final EventParticipationTransactionService eventParticipationTransactionService;
    private final EventParticipationDeadLetterPublisher deadLetterPublisher;
    private final MeterRegistry meterRegistry;
    private final Semaphore databaseWriterPermits = new Semaphore(DATABASE_WRITER_CONCURRENCY);

    @Override
    public void onMessage(final MapRecord<String, String, String> record) {
        process(record, 1);
    }

    void process(final MapRecord<String, String, String> record, final long deliveryAttempt) {
        boolean acquired = false;
        try {
            databaseWriterPermits.acquire();
            acquired = true;
            processWithinConcurrencyLimit(record, deliveryAttempt);
        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();
            log.warn("이벤트 신청 메시지 처리가 중단되었습니다. streamId={}", record.getId());
        } finally {
            if (acquired) {
                databaseWriterPermits.release();
            }
        }
    }

    void moveToDlq(
            final MapRecord<String, String, String> record,
            final long deliveryAttempt,
            final Throwable failure
    ) {
        try {
            if (deadLetterPublisher.publish(record, deliveryAttempt, failure)) {
                log.error(
                        "이벤트 신청 메시지를 DLQ로 이동했습니다. streamId={}, attempts={}, failureType={}",
                        record.getId(),
                        deliveryAttempt,
                        failure.getClass()
                                .getName()
                );
            }
        } catch (RuntimeException dlqFailure) {
            log.error(
                    "이벤트 신청 메시지 DLQ 이동 실패. streamId={}, attempts={}",
                    record.getId(),
                    deliveryAttempt,
                    dlqFailure
            );
        }
    }

    private void processWithinConcurrencyLimit(
            final MapRecord<String, String, String> record,
            final long deliveryAttempt
    ) {
        EventParticipationMessage message = deserialize(record, deliveryAttempt);
        if (message == null) {
            return;
        }

        try {
            eventParticipationTransactionService.participate(
                    message.participationRequestId(),
                    message.eventId(),
                    new LoginMember(message.memberId()),
                    message.claimedAt(),
                    new EventParticipateRequest(message.answers())
            );
            acknowledge(record);
            meterRegistry.counter(PROCESSED_METRIC)
                    .increment();
        } catch (DataIntegrityViolationException exception) {
            handleDataIntegrityViolation(record, message, deliveryAttempt, exception);
        } catch (RuntimeException exception) {
            handleFailure(record, message, deliveryAttempt, exception);
        }
    }

    private EventParticipationMessage deserialize(
            final MapRecord<String, String, String> record,
            final long deliveryAttempt
    ) {
        String payload = record.getValue()
                .get(PAYLOAD_FIELD);
        if (payload == null) {
            log.error("이벤트 신청 메시지 payload 누락. streamId={}", record.getId());
            moveToDlq(
                    record,
                    deliveryAttempt,
                    new IllegalArgumentException("이벤트 신청 메시지 payload가 누락되었습니다.")
            );
            return null;
        }

        try {
            return objectMapper.readValue(payload, EventParticipationMessage.class);
        } catch (JsonProcessingException exception) {
            log.error("이벤트 신청 메시지 역직렬화 실패. streamId={}", record.getId(), exception);
            moveToDlq(record, deliveryAttempt, exception);
            return null;
        }
    }

    private void handleDataIntegrityViolation(
            final MapRecord<String, String, String> record,
            final EventParticipationMessage message,
            final long deliveryAttempt,
            final DataIntegrityViolationException exception
    ) {
        try {
            if (guestRepository.countByParticipationRequestIdIncludingDeleted(message.participationRequestId()) > 0) {
                acknowledge(record);
                meterRegistry.counter(PROCESSED_METRIC)
                        .increment();
                return;
            }
        } catch (DataAccessException lookupFailure) {
            handleFailure(record, message, deliveryAttempt, lookupFailure);
            return;
        }

        recordFailure(false);
        moveToDlq(record, deliveryAttempt, exception);
    }

    private void handleFailure(
            final MapRecord<String, String, String> record,
            final EventParticipationMessage message,
            final long deliveryAttempt,
            final RuntimeException exception
    ) {
        boolean retryable = isRetryable(exception);
        recordFailure(retryable);

        if (retryable && deliveryAttempt < MAX_DELIVERY_ATTEMPTS) {
            log.warn(
                    "이벤트 신청 메시지 DB 저장 실패. Pending 재처리 대기. "
                            + "streamId={}, participationRequestId={}, eventId={}, memberId={}, attempt={}",
                    record.getId(),
                    message.participationRequestId(),
                    message.eventId(),
                    message.memberId(),
                    deliveryAttempt,
                    exception
            );
            return;
        }

        moveToDlq(record, deliveryAttempt, exception);
    }

    private boolean isRetryable(final RuntimeException exception) {
        return exception instanceof DataAccessException
                && !(exception instanceof DataIntegrityViolationException);
    }

    private void recordFailure(final boolean retryable) {
        meterRegistry.counter(
                        FAILURE_METRIC,
                        "retryable",
                        Boolean.toString(retryable)
                )
                .increment();
    }

    private void acknowledge(final MapRecord<String, String, String> record) {
        redisTemplate.opsForStream()
                .acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId());
    }
}
