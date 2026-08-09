package com.ahmadda.infra.redis;

import com.ahmadda.application.EventParticipationTransactionService;
import com.ahmadda.application.dto.EventParticipateRequest;
import com.ahmadda.application.dto.EventParticipationMessage;
import com.ahmadda.application.dto.LoginMember;
import com.ahmadda.domain.event.GuestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import static com.ahmadda.infra.redis.EventParticipationStreamConfig.CONSUMER_GROUP;
import static com.ahmadda.infra.redis.EventParticipationStreamConfig.STREAM_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEventParticipationStreamConsumer
        implements StreamListener<String, MapRecord<String, String, String>> {

    private static final String PAYLOAD_FIELD = "payload";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final GuestRepository guestRepository;
    private final EventParticipationTransactionService eventParticipationTransactionService;

    @Override
    public void onMessage(final MapRecord<String, String, String> record) {
        EventParticipationMessage message = deserialize(record);
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
        } catch (DataIntegrityViolationException exception) {
            acknowledgeIfAlreadyProcessed(record, message, exception);
        } catch (RuntimeException exception) {
            log.error(
                    "이벤트 신청 메시지 처리 실패. streamId={}, participationRequestId={}, eventId={}, memberId={}",
                    record.getId(),
                    message.participationRequestId(),
                    message.eventId(),
                    message.memberId(),
                    exception
            );
        }
    }

    private EventParticipationMessage deserialize(final MapRecord<String, String, String> record) {
        String payload = record.getValue()
                .get(PAYLOAD_FIELD);
        if (payload == null) {
            log.error("이벤트 신청 메시지 payload 누락. streamId={}", record.getId());
            return null;
        }

        try {
            return objectMapper.readValue(payload, EventParticipationMessage.class);
        } catch (JsonProcessingException exception) {
            log.error("이벤트 신청 메시지 역직렬화 실패. streamId={}", record.getId(), exception);
            return null;
        }
    }

    private void acknowledgeIfAlreadyProcessed(
            final MapRecord<String, String, String> record,
            final EventParticipationMessage message,
            final DataIntegrityViolationException exception
    ) {
        if (guestRepository.existsByParticipationRequestIdIncludingDeleted(message.participationRequestId())) {
            acknowledge(record);
            return;
        }

        log.error(
                "이벤트 신청 DB 무결성 위반. streamId={}, participationRequestId={}",
                record.getId(),
                message.participationRequestId(),
                exception
        );
    }

    private void acknowledge(final MapRecord<String, String, String> record) {
        redisTemplate.opsForStream()
                .acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId());
    }
}
