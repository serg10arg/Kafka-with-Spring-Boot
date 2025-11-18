package com.learnkafka.config;

import com.learnkafka.service.FailureService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.List;

@Configuration
@Slf4j
public class LibraryEventsConsumerConfig {

    @Autowired
    LibraryEventService libraryEventService;

    @Autowired
    KafkaProperties kafkaProperties;

    @Autowired
    KafkaTemplate kafkaTemplate;

    @Autowired
    FailureService failureService;

    @Value("library-events.RETRY")
    private String retryTopic;

    @Value("library-events.DLT")
    private String deadLetterTopic;

    // 1. Implementar la Estrategia de Recuperación (Publishing Recoverer)
    public DeadLetterPublishingRecoverer publishingRecoverer() {

        // 1.1 Este recuperador toma el mensaje fallido y lo re-publica
        return new DeadLetterPublishingRecoverer(this.kafkaTemplate, (record, exception) -> {
            log.error("Exception in publishingRecoverer: {}", exception.getMessage(), exception);

            // 1.2 Si el error es recuperable (ej. DB temporalmente caida)
            if (exception.getCause() instanceof RecoverableDataAccessException) {

                // 1.3 envia el mensaje al topic de reintentos
                return new TopicPartition(retryTopic, record.partition());
            } else {

                // 1.4 Para todos los demas errores, envia al dead letter topic
                return new TopicPartition(deadLetterTopic, record.partition());
            }
        });
    }

    // 2. Configurar el Manejador de Errores principal (Error Handler)
    public DefaultErrorHandler errorHandler() {

        // 2.1 Define una politica de reintentos: 2 intentos con 1 segundo de espera entre ellos.
        var fixedBackOff = new FixedBackOff(1000L, 2L);

        // 2.2 Crea el manejandor de errores, pasandole el recuperador y la politica de reintentos
        var defaultErrorHandler = new DefaultErrorHandler(publishingRecoverer(), fixedBackOff);

        // 2.3 Define excepciones que NO deben ser reintentadas (ej. un mensaje malformado)
        var exceptionsToIgnore = List.of(IllegalArgumentException.class);
        exceptionsToIgnore.forEach(defaultErrorHandler::addNotRetryableExceptions);

        // 2.4 Añade un listener para registrar cada intento fallido.
        defaultErrorHandler.setRetryListeners(
                (record, exception, deliveryAttempt) -> {
                    log.info("Failed Record in retry listener exception : {}" , exception.getMessage(), deliveryAttempt);
                });

        return defaultErrorHandler;
    }


    // 3. Construir la Fábrica de Listeners Personalizada

    @Bean
    @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ObjectProvider<ConsumerFactory<Object, Object>> kafkaConsumerFactory) {

        // 3.1 Crear una nueva instancia de la fabrica
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();

        // 3.2 Aplica toda la configuracion por defecto de Spring Boot
        configurer.configure(factory, kafkaConsumerFactory
                .getIfAvailable(() -> new DefaultKafkaConsumerFactory<>(this.kafkaProperties.buildConsumerProperties())));

        // 3.3 Establece la concurrencia (numero de hilos por listener)
        factory.setConcurrency(3);

        // 3.4 Asigna nuestro manejador de errores personalizado
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }
}
