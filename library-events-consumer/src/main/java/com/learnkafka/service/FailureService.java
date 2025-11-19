package com.learnkafka.service;

import com.learnkafka.model.FailureRecord;
import com.learnkafka.repository.FailureRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FailureService {

    private FailureRecordRepository failureRecordRepository;

    public FailureService(FailureRecordRepository failureRecordRepository) {
        this.failureRecordRepository = failureRecordRepository;
    }

    public void saveFailedRecord(ConsumerRecord<Integer, String> record, Exception exception, String status) {

        var failureRecord = FailureRecord.builder()
                .topic(record.topic())
                .key_value(record.key())
                .errorRecord(record.value())
                .partition(record.partition())
                .offset_value(record.offset())
                .exception(exception.getMessage())
                .status(status)
                .build();

        failureRecordRepository.save(failureRecord);

        log.info("Failure record saved: {}", failureRecord);
    }
}
