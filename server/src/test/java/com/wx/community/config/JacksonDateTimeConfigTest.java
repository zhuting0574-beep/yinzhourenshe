package com.wx.community.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonDateTimeConfigTest {
    @Test
    void serializesDatabaseAndJavaTimeValuesAsShanghaiStrings() throws Exception {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonDateTimeConfig().dateTimeCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("timestamp", Timestamp.from(Instant.parse("2026-11-17T05:01:39Z")));
        values.put("localDateTime", LocalDateTime.of(2026, 11, 17, 13, 1, 39));
        values.put("date", LocalDate.of(2026, 11, 17));
        values.put("time", LocalTime.of(13, 1, 39));

        String json = mapper.writeValueAsString(values);

        assertTrue(json.contains("\"timestamp\":\"2026-11-17 13:01:39\""));
        assertTrue(json.contains("\"localDateTime\":\"2026-11-17 13:01:39\""));
        assertTrue(json.contains("\"date\":\"2026-11-17\""));
        assertTrue(json.contains("\"time\":\"13:01:39\""));
    }
}
