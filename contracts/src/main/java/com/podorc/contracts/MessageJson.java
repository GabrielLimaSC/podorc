package com.podorc.contracts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The {@link ObjectMapper} configuration every module must use to (de)serialize podorc messages,
 * so the wire format stays identical on both sides of the Kafka topics.
 *
 * <ul>
 *   <li>{@code java.time} values as ISO-8601 strings, never numeric timestamps;</li>
 *   <li>unknown properties are ignored on read — new optional fields must not break older
 *       consumers. Strict rejection is the job of the versioned JSON schemas in
 *       {@code resources/schema/}, applied at the boundary that needs it.</li>
 * </ul>
 *
 * <p>{@link #MAPPER} is immutable after construction and safe to share. Use {@link #newMapper()}
 * only when a caller needs to further customize a copy.
 */
public final class MessageJson {

    /** Shared, pre-configured, thread-safe mapper. Do not reconfigure. */
    public static final ObjectMapper MAPPER = newMapper();

    private MessageJson() {
    }

    /** A fresh mapper with the podorc message configuration, for callers that need to extend it. */
    public static ObjectMapper newMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
