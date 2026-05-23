package com.socialfeed.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topics.post-created}")
    private String postCreatedTopic;

    @Value("${kafka.topics.post-created-dlt}")
    private String postCreatedDltTopic;

    @Value("${kafka.topics.notifications:notifications}")
    private String notificationsTopic;

    @Value("${kafka.topics.notifications-dlt:notifications-dlt}")
    private String notificationsDltTopic;

    @Bean
    public NewTopic postCreatedTopic() {
        return TopicBuilder.name(postCreatedTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic postCreatedDltTopic() {
        return TopicBuilder.name(postCreatedDltTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name(notificationsTopic)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationsDltTopic() {
        return TopicBuilder.name(notificationsDltTopic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
