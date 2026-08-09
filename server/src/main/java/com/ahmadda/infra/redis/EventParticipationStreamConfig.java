package com.ahmadda.infra.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Configuration
@ConditionalOnProperty(
        name = "event-participation.stream.consumer.enabled",
        havingValue = "true"
)
public class EventParticipationStreamConfig {

    public static final String STREAM_KEY = "event-participation:stream";
    public static final String CONSUMER_GROUP = "event-participation-db-writers";

    private static final int CONSUMER_COUNT = 3;
    private static final String BOOTSTRAP_FIELD = "type";
    private static final String BOOTSTRAP_VALUE = "bootstrap";

    @Bean(name = "eventParticipationStreamExecutor")
    public ThreadPoolTaskExecutor eventParticipationStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("event-participation-stream-");
        executor.setCorePoolSize(CONSUMER_COUNT);
        executor.setMaxPoolSize(CONSUMER_COUNT);
        executor.setQueueCapacity(0);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
    eventParticipationStreamMessageListenerContainer(
            final RedisConnectionFactory redisConnectionFactory,
            final StringRedisTemplate redisTemplate,
            final RedisEventParticipationStreamConsumer streamConsumer,
            @Qualifier("eventParticipationStreamExecutor")
            final ThreadPoolTaskExecutor eventParticipationStreamExecutor
    ) {
        initializeConsumerGroup(redisTemplate);

        var options = StreamMessageListenerContainer
                .StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(1))
                .batchSize(1)
                .executor(eventParticipationStreamExecutor)
                .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);

        String consumerInstanceId = UUID.randomUUID()
                .toString();
        for (int index = 0; index < CONSUMER_COUNT; index++) {
            container.receive(
                    Consumer.from(CONSUMER_GROUP, consumerInstanceId + "-" + index),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                    streamConsumer
            );
        }

        container.start();
        return container;
    }

    private void initializeConsumerGroup(final StringRedisTemplate redisTemplate) {
        RecordId bootstrapRecordId = null;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(STREAM_KEY))) {
            bootstrapRecordId = redisTemplate.opsForStream()
                    .add(STREAM_KEY, Map.of(BOOTSTRAP_FIELD, BOOTSTRAP_VALUE));
        }

        boolean consumerGroupExists = redisTemplate.opsForStream()
                .groups(STREAM_KEY)
                .stream()
                .anyMatch(group -> CONSUMER_GROUP.equals(group.groupName()));

        if (!consumerGroupExists) {
            redisTemplate.opsForStream()
                    .createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
        }

        if (bootstrapRecordId != null) {
            redisTemplate.opsForStream()
                    .delete(STREAM_KEY, bootstrapRecordId);
        }
    }
}
