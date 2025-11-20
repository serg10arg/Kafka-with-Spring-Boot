package com.learnkafka.scheduler;

import com.learnkafka.config.LibraryEventsConsumerConfig;
import com.learnkafka.model.FailureRecord;
import com.learnkafka.repository.FailureRecordRepository;
import com.learnkafka.service.LibraryEventService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RetryScheduler {

    private final LibraryEventService libraryEventService;
    private final FailureRecordRepository failureRecordRepository;

    public RetryScheduler(LibraryEventService libraryEventService, FailureRecordRepository failureRecordRepository) {
        this.libraryEventService = libraryEventService;
        this.failureRecordRepository = failureRecordRepository;
    }

    @Scheduled(fixedRate = 10000) // Ejecuta la tarea cada 10 segundos
    public void retryFailedRecords() {
        log.info("Retrying failed records started!");

        // 1. Define el estado de los registros a buscar.
        var status = LibraryEventsConsumerConfig.RETRY;

        // 2. Busca en la base de datos todos los registros marcados para reintento
        failureRecordRepository.findAllByStatus(status)
                .forEach(failureRecord -> {
                    log.info("Retrying record: {}", failureRecord);
                    try {
                        // 3. Reconstruye el mensaje original de Kafka
                        var consumerRecord = buildConsumerRecord(failureRecord);

                        // 4. Llama a la misma logica de negocio para re-procesar el mensaje
                        libraryEventService.proccessLibraryEvent(consumerRecord);

                        // 5. Si tiene exito, actualiza el estado para evitar futuros reintentos.
                        failureRecord.setStatus(LibraryEventsConsumerConfig.SUCCESS);
                        failureRecordRepository.save(failureRecord);
                        log.info("Retry successful for record: {}", failureRecord);
                    } catch (Exception e) {
                        log.error("Exception during retry for record {}: {}", failureRecord.getBookId(), e.getMessage());
                    }
                });
    }

    private ConsumerRecord<Integer, String> buildConsumerRecord(FailureRecord failureRecord) {

        return new ConsumerRecord<>(failureRecord.getTopic(),
                failureRecord.getPartition(), failureRecord.getOffset_value(), failureRecord.getKey_value(),
                failureRecord.getErrorRecord());

    }
}
