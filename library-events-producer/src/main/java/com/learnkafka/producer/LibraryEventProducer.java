package com.learnkafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.domain.LibraryEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class LibraryEventProducer {

    KafkaTemplate<Integer, String> kafkaTemplate;
    ObjectMapper objectMapper;

    public LibraryEventProducer(KafkaTemplate<Integer, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("library-events")
    private String topic;

    //asíncrona
    public CompletableFuture<SendResult<Integer, String>> sendLibraryEvent(LibraryEvent libraryEvent) throws JsonProcessingException {

        Integer key = libraryEvent.libraryEventId();
        String value = objectMapper.writeValueAsString(libraryEvent);

        var completableFuture = kafkaTemplate.sendDefault(key, value);

        return completableFuture
                .whenComplete((SendResult, throwable) -> {
                    if (throwable != null) {
                        handleFailure(key, value, throwable);
                    } else {
                        handleSuccess(key, value, SendResult);
                    }
                });

    }

    public CompletableFuture<SendResult<Integer, String>> sendLibraryEvent_Approach2(LibraryEvent libraryEvent) throws  JsonProcessingException {

        Integer key = libraryEvent.libraryEventId();
        String value = objectMapper.writeValueAsString(libraryEvent);

        //Representación completa de un mensaje en Kafka. Nos da control total sobre todos sus componentes.
        ProducerRecord<Integer, String> producerRecord = buildProducerRecord(key, value, topic);

        var completableFuture = kafkaTemplate.send(producerRecord);
        return completableFuture
                .whenComplete((SendResult, throwable) -> {
                    if (throwable != null) {
                        handleFailure(key, value, throwable);
                    } else {
                        handleSuccess(key, value, SendResult);
                    }
                });
    }


    private ProducerRecord<Integer, String> buildProducerRecord (Integer key, String value, String topic) {

        List<Header> recordHeader = List.of(new RecordHeader("event-soruce", "scanner".getBytes()));

        return  new ProducerRecord<>(topic, null, key, value, recordHeader);
    }

    public SendResult<Integer, String> sendLibraryEventSynchronous(LibraryEvent libraryEvent) throws JsonProcessingException,
            ExecutionException, InterruptedException, TimeoutException {

        Integer key = libraryEvent.libraryEventId();
        String value = objectMapper.writeValueAsString(libraryEvent);
        SendResult<Integer, String> sendResult = null;
        try {
            sendResult = kafkaTemplate.sendDefault(key, value).get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException e) {
            log.error("ExecutionExc eption/InterruptedException Sending the Message and the exception is {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Exception Sending the Message and the exception is {}", e.getMessage());
            throw e;
        }

        return sendResult;

    }

    private void handleFailure(Integer key, String value, Throwable throwable) {
        log.error("Error sending the message and the exception is {}", throwable.getMessage());
    }

    private void handleSuccess(Integer key, String value, SendResult<Integer, String> result) {
        log.info("Message sent successfully for the key: {} and the value is {}, partition is {}", key, value, result.getRecordMetadata().partition());
    }


}
