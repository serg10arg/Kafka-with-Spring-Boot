package com.learnkafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.learnkafka.service.LibraryEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LibraryEventsConsumer {

    private final LibraryEventService libraryEventService;

    public LibraryEventsConsumer(LibraryEventService libraryEventService) {
        this.libraryEventService = libraryEventService;

    }

    @KafkaListener(topics = {"library-events"}, // 1. Especifica el topic a escuchar
    groupId = "library-events-listener-group",  // 2. Asigna el consumidor a un grupo
    autoStartup = "${libraryListener.startup:true}") // 3. Controla el inicio automatico
    public void onMessage(ConsumerRecord<Integer, String> consumerRecord) throws JsonProcessingException {

        // 4. Registra el mensaje recibido para trazabilidad
        log.info("ConsumerRecord : {} ", consumerRecord);

        // 5. Delega el procesamiento completo a la capa de servicio
        libraryEventService.proccessLibraryEvent(consumerRecord);
    }
}
