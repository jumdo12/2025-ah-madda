package com.ahmadda.infra.notification.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "mail.outbox.rabbitmq")
public record EmailOutboxRabbitProperties(
        @DefaultValue("false")
        boolean enabled,
        @DefaultValue("email.outbox")
        String exchange,
        @DefaultValue("email.outbox.dispatch")
        String queue,
        @DefaultValue("email.dispatch")
        String routingKey
) {
}
