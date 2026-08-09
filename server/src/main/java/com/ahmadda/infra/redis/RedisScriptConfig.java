package com.ahmadda.infra.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {

    @Bean
    public DefaultRedisScript<Long> claimEventSeatScript() {
        return createScript("redis/claim-event-seat.lua");
    }

    @Bean
    public DefaultRedisScript<Long> releaseEventSeatScript() {
        return createScript("redis/release-event-seat.lua");
    }

    @Bean
    public DefaultRedisScript<Long> synchronizeEventSeatCapacityScript() {
        return createScript("redis/synchronize-event-seat-capacity.lua");
    }

    @Bean
    public DefaultRedisScript<Long> moveEventParticipationToDlqScript() {
        return createScript("redis/move-event-participation-to-dlq.lua");
    }

    private DefaultRedisScript<Long> createScript(final String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }
}
