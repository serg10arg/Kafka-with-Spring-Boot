package com.learnkafka.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.domain.LibraryEvent;
import com.learnkafka.producer.LibraryEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import util.TestUtil;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LibraryEventsController.class)
public class LibraryEventControllerUnitTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    LibraryEventProducer libraryEventProducer;

    @Test
    void postLibraryEvent() throws Exception {

        //given
        LibraryEvent libraryEvent = TestUtil.libraryEventRecord();
        String json = objectMapper.writeValueAsString(libraryEvent);
        when(libraryEventProducer.sendLibraryEvent(isA(LibraryEvent.class)))
                .thenReturn(null);

        //expect
        mockMvc.perform(post("/v1/libraryevent")
                .content(json)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

    }

    @Test
    void postLibraryEvent_4xx() throws Exception {

        //given

        LibraryEvent libraryEvent = TestUtil.libraryEventRecordWithInvalidBook();
        String json = objectMapper.writeValueAsString(libraryEvent);
        when(libraryEventProducer.sendLibraryEvent(isA(LibraryEvent.class))).thenReturn(null);

        //Expect

        String expectedErrorMessage = "book.bookId - must not be null, book.bookName - must not be blank";
        mockMvc.perform(post("/v1/libraryevent")
                .content(json)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(expectedErrorMessage));

    }

    @Test
    void updateLibraryEvent() throws Exception {

        //given

        String json = objectMapper.writeValueAsString(TestUtil.libraryEventRecordUpdate());
        when(libraryEventProducer.sendLibraryEvent_Approach2(isA(LibraryEvent.class))).thenReturn(null);

        //expect
        mockMvc.perform(
                        put("/v1/libraryevent")
                                .content(json)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

    }

    @Test
    void updateLibraryEvent_withNullLibraryEventId() throws Exception {

        //given

        String json = objectMapper.writeValueAsString(TestUtil.libraryEventRecordUpdateWithNullLibraryEventId());
        when(libraryEventProducer.sendLibraryEvent_Approach2(isA(LibraryEvent.class))).thenReturn(null);

        //Expect
        mockMvc.perform(
                put("/v1/libraryevent")
                        .content(json)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string("Please pass the LibraryEventId"));
    }

    @Test
    void updateLibraryEvent_withNullInvalidEventType() throws Exception {

        //given

        String json = objectMapper.writeValueAsString(TestUtil.newLibraryEventRecordWithLibraryEventId());

        //Expect
        mockMvc.perform(
                put("/v1/libraryevent")
                        .content(json)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string("Only UPDATE event type is supported"));
    }

}
