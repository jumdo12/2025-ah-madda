package com.ahmadda.infra.redis;

import com.ahmadda.application.EventSeatInventory;
import com.ahmadda.application.dto.SeatClaimResult;
import com.ahmadda.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisEventSeatInventory implements EventSeatInventory {

    private static final String IMMEDIATE_MODE = "IMMEDIATE";
    private static final String APPROVAL_REQUIRED_MODE = "APPROVAL_REQUIRED";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> claimEventSeatScript;

    @Override
    public SeatClaimResult claim(final Long eventId, final Long memberId) {
        try {
            Long result = redisTemplate.execute(
                    claimEventSeatScript,
                    List.of(inventoryKey(eventId), participantsKey(eventId)),
                    memberId.toString()
            );

            return SeatClaimResult.fromCode(result);
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("이벤트 잔여석 저장소에 연결할 수 없습니다.");
        }
    }

    @Override
    public void initialize(
            final Long eventId,
            final int maxCapacity,
            final boolean approvalRequired
    ) {
        String mode = approvalRequired ? APPROVAL_REQUIRED_MODE : IMMEDIATE_MODE;

        try {
            redisTemplate.delete(participantsKey(eventId));
            redisTemplate.opsForHash()
                    .putAll(
                            inventoryKey(eventId),
                            Map.of(
                                    "mode", mode,
                                    "remaining", Integer.toString(maxCapacity)
                            )
                    );
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("이벤트 잔여석 저장소를 초기화할 수 없습니다.");
        }
    }

    private String inventoryKey(final Long eventId) {
        return "event:{%d}:seat-inventory".formatted(eventId);
    }

    private String participantsKey(final Long eventId) {
        return "event:{%d}:seat-participants".formatted(eventId);
    }
}
