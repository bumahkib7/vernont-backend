package com.vernont.infrastructure.messaging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties

private val logger = KotlinLogging.logger {}

/**
 * Kafka/Redpanda configuration for development environment.
 */
@Configuration
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "kafka", matchIfMissing = true)
@EnableConfigurationProperties(MessagingProperties::class)
class KafkaConfig(
    private val messagingProperties: MessagingProperties
) {

    @Bean
    fun producerFactory(): ProducerFactory<String, String> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to messagingProperties.kafka.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
        logger.info { "Configuring Kafka producer with bootstrap servers: ${messagingProperties.kafka.bootstrapServers}" }
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory())
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to messagingProperties.kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to messagingProperties.kafka.consumerGroupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to messagingProperties.kafka.autoOffsetReset,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to messagingProperties.kafka.enableAutoCommit
        )
        logger.info { "Configuring Kafka consumer with bootstrap servers: ${messagingProperties.kafka.bootstrapServers}" }
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.setConsumerFactory(consumerFactory())
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.setConcurrency(3)
        return factory
    }
}
