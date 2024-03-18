package com.stonematrix.ticket.endpoints;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;

/**
 * Using this object mapper to make sure application de-couple with any host specific JVM timezone.
 * We are now assuming all time is UTC time.
 * If in future we shall support multiple timezone, we can modify this objectmapper.
 */
@Provider
@Component
public class DateObjectMapperProvider implements ContextResolver<ObjectMapper> {

    private static final String DATEFORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final TimeZone TIMEZONE =  TimeZone.getTimeZone("UTC");

    private final ObjectMapper objectMapper;
    @Inject
    public DateObjectMapperProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleModule module = new SimpleModule();

        module.addDeserializer(Date.class,
                new JsonDeserializer<>() {
                    @Override
                    public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
                        String date = jsonParser.getText();
                        try {
                            SimpleDateFormat dateFormat = new SimpleDateFormat(DATEFORMAT);
                            dateFormat.setTimeZone(TIMEZONE);

                            return dateFormat.parse(date);
                        } catch (ParseException e) {
                            throw new IOException(e);
                        }
                    }
                });
        module.addSerializer(Date.class,
                new JsonSerializer<>() {
                    @Override
                    public void serialize(Date date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
                        SimpleDateFormat dateFormat = new SimpleDateFormat(DATEFORMAT);
                        dateFormat.setTimeZone(TIMEZONE);

                        String formattedDate = dateFormat.format(date);
                        jsonGenerator.writeString(formattedDate);
                    }
                });
        objectMapper.registerModule(module);
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }
}
