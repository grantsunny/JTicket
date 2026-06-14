package com.jticket.endpoints;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jticket.api.model.Seat;
import com.jticket.persist.EventsRepository;

import io.jsonwebtoken.Jwts;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

class EventsApiResourceTest {

    private EventsApiResource resource;
    private KeyPair keyPair;
    private EventsRepository repository;
    private UUID checkedInEventId;
    private UUID checkedInSessionId;
    private UUID checkedInSeatId;
    private int affectedRows;
    private Seat loadedSeat;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        repository = (EventsRepository) Proxy.newProxyInstance(
                EventsRepository.class.getClassLoader(),
                new Class<?>[]{EventsRepository.class},
                (proxy, method, args) -> {
                    if ("checkInSeat".equals(method.getName())) {
                        checkedInEventId = (UUID) args[0];
                        checkedInSessionId = (UUID) args[1];
                        checkedInSeatId = (UUID) args[2];
                        return affectedRows;
                    }
                    if ("loadSeatInEvent".equals(method.getName()))
                        return loadedSeat;
                    return null;
                });
        resource = new EventsApiResource();
        setField(resource, "repository", repository);
        setField(resource, "publicKeyPem",
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        setField(resource, "keyAlgorithm", "RSA");
    }

    @Test
    void checkInUsesEventSessionAndSeatIdentifiers() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Seat seat = new Seat().id(seatId);
        affectedRows = 1;
        loadedSeat = seat;

        Response response = resource.checkIn(token(eventId, sessionId, seatId),
                eventId.toString(), sessionId.toString());

        assertEquals(200, response.getStatus());
        assertNotNull(((Seat) response.getEntity()).getCheckedInTimestamp());
        assertEquals(eventId, checkedInEventId);
        assertEquals(sessionId, checkedInSessionId);
        assertEquals(seatId, checkedInSeatId);
    }

    @Test
    void checkInRejectsAlreadyCheckedInOrUnknownSeat() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        affectedRows = 0;

        assertThrows(BadRequestException.class, () -> resource.checkIn(
                token(eventId, sessionId, seatId), eventId.toString(), sessionId.toString()));
    }

    private String token(UUID eventId, UUID sessionId, UUID seatId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer("JTicket")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60_000))
                .claim("event", eventId.toString())
                .claim("session", sessionId.toString())
                .claim("seat", seatId.toString())
                .signWith(keyPair.getPrivate())
                .compact();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
