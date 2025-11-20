package com.learnkafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.model.Book;
import com.learnkafka.model.LibraryEvent;
import com.learnkafka.model.LibraryEventType;
import com.learnkafka.repository.FailureRecordRepository;
import com.learnkafka.repository.LibraryEventsRepository;
import com.learnkafka.service.LibraryEventService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

@SpringBootTest // 1. Arrancar el contexto completo de la aplicacion
@EmbeddedKafka ( // 2. Inicia un broker de kafka en memoria
        topics = {"library-events", "library-events.RETRY", "library-events.DLT"},
        partitions = 3 // Define el numero de particiones
)
@TestPropertySource(properties = { // 3. Sobrescribe las propiedades de la aplicacion para la prueba}
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "retryListener.startup=false"}) // Util para deshabilitar otros listeners que no se estan probando
public class LibraryEventsConsumerIntegrationTest {

    @Value("${topics.retry}")
    private String retryTopic;

    @Value("${topics.dlt}")
    private String deadLetterTopic;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    KafkaTemplate<Integer, String> kafkaTemplate;

    @Autowired
    KafkaListenerEndpointRegistry endpointRegistry;

    @MockitoSpyBean // Crea un espia del bean real para verificar su comportamiento
    LibraryEventsConsumer libraryEventsConsumerSpy;

    @MockitoSpyBean // Crea un espia del bean real para verificar su comportamiento
    LibraryEventService libraryEventServiceSpy;

    @Autowired
    LibraryEventsRepository libraryEventsRepository;

    @Autowired
    FailureRecordRepository failureRecordRepository;

    @Autowired
    ObjectMapper objectMapper;

    private Consumer<Integer, String> consumer;

    @BeforeEach
    void setUp() {

        // Buscar el contenedor del listener especifico que queremos probar
        var container = endpointRegistry.getListenerContainers()
                .stream().filter(messageListenerContainer ->
                        Objects.equals(messageListenerContainer.getGroupId(), "library-events-listener-group"))
                .collect(Collectors.toList()).get(0);

        // Espera hasta que kafka haya asignado las particiones del topic a nuestro listener
        // ESTO ES CRITICO para evitar que los menajes se publiquen antes de que el consumidor este listo.
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {

        //Limpia los datos de la base de datos para asegurar la independencia de los tests.
        libraryEventsRepository.deleteAll();
        failureRecordRepository.deleteAll();
    }

    @Test
    void publishNewLibraryEvent() throws ExecutionException, InterruptedException, JsonProcessingException {

        // given: prepara los datos y publica un menaje en el topic principal
        String json = "{\"libraryEventId\":null,\"libraryEventType\":\"NEW\",\"book\":" +
                "{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"}}";
        kafkaTemplate.sendDefault(json).get();

        // when: espera un tiempo prudencial para que el conumidor procese el mensaje.
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS );

        // then: verifica los resultados
        // 1. Verifica que los metodos del consumidor y del servicio fueron llamados una vez
        verify(libraryEventsConsumerSpy, times(1)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventServiceSpy, times(1)).proccessLibraryEvent(isA(ConsumerRecord.class));

        // 2. Verifica que el evento fue guardado correctamente en la base de datos
        List<LibraryEvent> libraryEventList = (List<LibraryEvent>) libraryEventsRepository.findAll();
        assert libraryEventList.size() == 1;
        libraryEventList.forEach(libraryEvent -> {
            assert libraryEvent.getLibraryEventId() != null;
            assertEquals(456, libraryEvent.getBook().getBookId());
        });
    }

    @Test
    void publishUpdateLibraryEvent() throws JsonProcessingException, ExecutionException, InterruptedException {

        // given
        String json = "{\"libraryEventId\":null,\"libraryEventType\":\"NEW\",\"book\":{\"bookId\":456,\"bookName\"" +
                ":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"}}";

        LibraryEvent libraryEvent = objectMapper.readValue(json, LibraryEvent.class);
        libraryEvent.getBook().setLibraryEvent(libraryEvent);
        libraryEventsRepository.save(libraryEvent);

        // publish the update LibraryEvent
        Book updateBook = Book.builder()
                .bookId(456)
                .bookName("Kafka Using Spring Boot 2.0")
                .bookAuthor("Dilip")
                .build();
        libraryEvent.setLibraryEventType(LibraryEventType.UPDATE);
        libraryEvent.setBook(updateBook);
        String updateJson = objectMapper.writeValueAsString(libraryEvent);
        kafkaTemplate.sendDefault(libraryEvent.getLibraryEventId(), updateJson).get();

        // when
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        // then
        LibraryEvent persistedLibraryEvent = libraryEventsRepository.findById(libraryEvent.getLibraryEventId()).get();
        assertEquals("Kafka Using Spring Boot 2.0", persistedLibraryEvent.getBook().getBookName());
    }

    @Test
    void publishModifyLibraryEvent_Not_A_Valid_LibraryEventId() throws JsonProcessingException,
            InterruptedException, ExecutionException {

        // given: Publica un mensaje que causara un IllegalArgumentException
        Integer libraryEventId = 123;
        String json = "{\"libraryEventId\":" + libraryEventId + ",\"libraryEventType\":\"UPDATE\",\"book\":{\"bookId\"" +
                ":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"}}";
        System.out.println(json);
        kafkaTemplate.sendDefault(libraryEventId, json).get();

        // when: Espera que el procesamiento falle
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        // then: Verifica los resultados
        // 1. Verifica que los metodos del consumidor y del servicio fueron llamados una vez
        verify(libraryEventsConsumerSpy, times(1)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventServiceSpy, times(1)).proccessLibraryEvent(isA(ConsumerRecord.class));

        // 2. Verifica que el mensaje fallido fue enviado al Dead Letter Topic
        // crea un consumidor de prueba para leer del DLT
        Map<String, Object> configs = new HashMap<>(KafkaTestUtils.consumerProps("group2", "true", embeddedKafkaBroker));
        consumer = new DefaultKafkaConsumerFactory<>(configs, new IntegerDeserializer(), new StringDeserializer()).createConsumer();
        // Conecta nuestro consumidor de prueba al topic de DLT para empezar a escuchar
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, deadLetterTopic);

        // Obtiene el registro del DLT y verifica que es el menasje original
        ConsumerRecord<Integer, String> consumerRecord = KafkaTestUtils.getSingleRecord(consumer, deadLetterTopic);

        System.out.println("Consumer record in deadletter topic : " + consumerRecord.value());

        // Afirma que el contenido del mensaje encontado en el DLT es identico al original que causo el fallo
        assertEquals(json, consumerRecord.value());
        consumerRecord.headers()
                .forEach(header -> {
                    System.out.println("header key: " + header.key() + ", Header value: " + new String(header.value()));
                });
    }

    @Test
    void publishModifyLibraryEvent_Null_LibraryEventId() throws JsonProcessingException, InterruptedException, ExecutionException {

        // given
        Integer libraryEventId = null;
        String json = "{\"libraryEventId\":" + libraryEventId + ",\"libraryEventType\":\"UPDATE\",\"book\":{\"bookId\"" +
                ":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"}}";
        kafkaTemplate.sendDefault(libraryEventId, json).get();

        //when
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        verify(libraryEventsConsumerSpy, times(1)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventServiceSpy, times(1)).proccessLibraryEvent(isA(ConsumerRecord.class));

        Map<String, Object> configs = new HashMap<>(KafkaTestUtils.consumerProps("group3", "true", embeddedKafkaBroker));
        consumer = new DefaultKafkaConsumerFactory<>(configs, new IntegerDeserializer(), new StringDeserializer()).createConsumer();

        // Conecta nuestro consumidor de prueba al topic de DLT para empezar a escuchar
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, deadLetterTopic);

        ConsumerRecords<Integer, String> consumerRecords = KafkaTestUtils.getRecords(consumer);

        var deadletterList = new ArrayList<ConsumerRecord<Integer, String>>();
        consumerRecords.forEach(record -> {
            if (record.topic().equals(deadLetterTopic)) {
                deadletterList.add(record);
            }
        });

        var finalist = deadletterList.stream()
                .filter(record -> record.value().equals(json))
                .collect(Collectors.toList());
        assert finalist.size() == 1;


    }

    @Test
    void publishModifyLibraryEvent_999_LibraryEventId_sendsToRetryTopic() throws JsonProcessingException, InterruptedException,
            ExecutionException {

        // given
        Integer libraryEventId = 999;
        String json = "{\"libraryEventId\":" + libraryEventId + ",\"libraryEventType\":\"UPDATE\",\"book\"" +
                ":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"}}";
        kafkaTemplate.sendDefault(libraryEventId, json).get(); // .get hace la publiacion sincrona

        // when
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(3, TimeUnit.SECONDS);

        // then
        // 1. Verifica que los reintentos ocurrieron
        verify(libraryEventsConsumerSpy, atLeast(3)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventServiceSpy, atLeast(3)).proccessLibraryEvent(isA(ConsumerRecord.class));

        // 2. Crea un consumidor de prueba para espiar el retryTopic
        Map<String, Object> configs = new HashMap<>(KafkaTestUtils.consumerProps("group1", "true", embeddedKafkaBroker));
        consumer = new DefaultKafkaConsumerFactory<>(configs, new IntegerDeserializer(), new StringDeserializer()).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, retryTopic);

        // 3. Obtiene el registro del retrytopic
        ConsumerRecord<Integer, String> consumerRecord = KafkaTestUtils.getSingleRecord(consumer, retryTopic);

        // 4. Afirma que el contenido del mensaje encontado en el retryTopic es identico al original que causo el fallo
        assertEquals(json, consumerRecord.value());
        consumerRecord.headers()
                .forEach(header -> {
                    System.out.println("Header key : " + header.key() + ", Header value : " + new String(header.value()));
                });
    }

    @Test
    @Disabled
    void publishModifyLibraryEvent_999_LibraryEventId_failureRecord() throws JsonProcessingException, InterruptedException,
            ExecutionException {

        // given: publica un mensaje diseñado para activar un error recuperable
        Integer libraryEventId = 999;
        String json = "{\"libraryEventId\":" + libraryEventId + ",\"libraryEventType\":\"UPDATE\",\"book\"" +
                ":{\"bookId\":456,\"bookName\":\"Kafka Using Spring Boot\",\"bookAuthor\":\"Dilip\"}}";
        kafkaTemplate.sendDefault(libraryEventId, json).get();

        // When: Espera un tiempo suficiente para que ocurran los reintentos y la recuperación.
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);

        // Then: Verifica los resultados.
        // 1. Verifica que los reintentos ocurrieron (1 intento original + 2 reintentos).
        verify(libraryEventsConsumerSpy, times(3)).onMessage(isA(ConsumerRecord.class));
        verify(libraryEventServiceSpy, times(3)).proccessLibraryEvent(isA(ConsumerRecord.class));

        // 2. La aserción clave: verifica que se creó exactamente un registro en la tabla de fallos.
        var failureCount = failureRecordRepository.count();
        assertEquals(1, failureCount);
        failureRecordRepository.findAll().forEach(failureRecord -> {
            System.out.println("failureRecord: " + failureCount);
        });
    }
}
