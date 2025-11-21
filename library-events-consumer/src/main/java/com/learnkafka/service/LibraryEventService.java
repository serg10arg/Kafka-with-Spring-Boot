package com.learnkafka.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.model.LibraryEvent;
import com.learnkafka.repository.LibraryEventsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.kafka.support.SendResult;

import java.util.Optional;

@Service
@Slf4j
public class LibraryEventService {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<Integer, String> kafkaTemplate;
    private final LibraryEventsRepository libraryEventsRepository;

    public LibraryEventService(ObjectMapper objectMapper, KafkaTemplate<Integer, String> kafkaTemplate,
                               LibraryEventsRepository libraryEventsRepository) {

        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.libraryEventsRepository = libraryEventsRepository;

    }

    //Crea un método público que será el punto de entrada desde el consumidor de Kafka.
    // Este método orquesta la deserialización, validación y persistencia.
    public void processLibraryEvent(ConsumerRecord<Integer, String> consumerRecord) throws JsonProcessingException {

        // 1. Deserializa el playload JSON a un objeto JAVA.
        LibraryEvent libraryEvent = objectMapper.readValue(consumerRecord.value(), LibraryEvent.class);
        
        // 2. (Opcional) Simula un error recuperable para probar la resiliencia.
        if (libraryEvent.getLibraryEventId() != null && (libraryEvent.getLibraryEventId() == 999)) {
            throw new RecoverableDataAccessException("Temporary Network Issue");
        }
        
        // 3. Delega el procesamiento segun el tipo de evento.
        switch (libraryEvent.getLibraryEventType()) {
            case NEW:
                save(libraryEvent);
                break;
            case UPDATE:
                validate(libraryEvent);
                save(libraryEvent);
                break;
            default:
                log.warn("Invalid Library Event Type {}", libraryEvent);
        }
    }

    private void validate(LibraryEvent libraryEvent) {

        // Valida que el ID no sea nulo para una actualizacion
        if (libraryEvent.getLibraryEventId() == null) {
            throw new IllegalArgumentException("Library Event Id is missing for an UPDATE event");
        }

        // Valida que el evento a actualizar realmente exista en la base de datos
        Optional<LibraryEvent> libraryEventOptional = libraryEventsRepository.findById(libraryEvent.getLibraryEventId());
        if (!libraryEventOptional.isPresent()) {
            throw new IllegalArgumentException("Not a valid library Event: ID does not exist");
        }
        log.info("Validation is successful for the library event : {}", libraryEventOptional.get());
    }


    private void save(LibraryEvent libraryEvent) {

        // Asegura la consistencia de la realcion bidireccional antes de guardar
        libraryEvent.getBook().setLibraryEvent(libraryEvent);
        libraryEventsRepository.save(libraryEvent);
        log.info("Successfully persisted the library event {}", libraryEvent);
    }

    public void handleRecovery(ConsumerRecord<Integer, String> record) {

        Integer key = record.key();
        String message = record.value();

        var completableFuture = kafkaTemplate.sendDefault(key, message);
        completableFuture.whenComplete((result, ex) -> {
            if (ex != null) {
                handleFailure(key, message, ex);
            } else {
                handleSuccess(key, message, result);
            }
        });
    }

    private void handleFailure(Integer key, String message, Throwable ex) {
        log.info("Error Sendind the Message for key: {}, value: {} and the exception is {}", key, message, ex.getMessage());
    }

    private void handleSuccess(Integer key, String value, SendResult<Integer, String> result) {
        log.info("Message Sent SuccessFully for the key : {} and the value is {} , partition is {}", key, value, result.getRecordMetadata().partition());
    }
}
