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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jticket.api.model.Area;
import com.jticket.api.model.Event;
import com.jticket.api.model.EventStatistics;
import com.jticket.api.model.Order;
import com.jticket.api.model.Session;
import com.jticket.api.model.TicketingSeat;
import com.jticket.api.model.SessionStatistics;
import com.jticket.persist.EventsRepository;
import com.jticket.persist.OrdersRepository;

import io.jsonwebtoken.Jwts;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

class EventsApiResourceTest {

    private EventsApiResource resource;
    private KeyPair keyPair;
    private EventsRepository repository;
    private OrdersRepository ordersRepository;
    private UUID checkedInEventId;
    private UUID checkedInSessionId;
    private UUID checkedInSeatId;
    private int affectedRows;
    private TicketingSeat loadedSeat;
    private List<TicketingSeat> loadedSeats;
    private UUID loadedSeatsSessionId;
    private UUID loadedSeatsAreaId;
    private Event loadedEvent;
    private Session loadedSession;
    private Area loadedArea;
    private EventStatistics loadedStatistics;
    private SessionStatistics loadedSessionStatistics;
    private List<Order> loadedOrders;
    private UUID loadedOrdersEventId;
    private int loadOrdersCalls;

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
                    if ("loadSeatInSession".equals(method.getName()))
                        return loadedSeat;
                    if ("loadSeatsInAreaOfSession".equals(method.getName())) {
                        loadedOrdersEventId = (UUID) args[0];
                        loadedSeatsSessionId = (UUID) args[1];
                        loadedSeatsAreaId = (UUID) args[2];
                        return loadedSeats;
                    }
                    if ("loadSession".equals(method.getName()))
                        return loadedSession;
                    if ("loadAreaInEvent".equals(method.getName()))
                        return loadedArea;
                    if ("loadEvent".equals(method.getName()))
                        return loadedEvent;
                    if ("loadEventStatistics".equals(method.getName()))
                        return loadedStatistics;
                    if ("loadSessionStatistics".equals(method.getName()))
                        return loadedSessionStatistics;
                    return null;
                });
        ordersRepository = (OrdersRepository) Proxy.newProxyInstance(
                OrdersRepository.class.getClassLoader(),
                new Class<?>[]{OrdersRepository.class},
                (proxy, method, args) -> {
                    if ("loadOrders".equals(method.getName()) && args.length == 1 && args[0] instanceof UUID) {
                        loadOrdersCalls++;
                        loadedOrdersEventId = (UUID) args[0];
                        return loadedOrders;
                    }
                    return null;
                });
        resource = new EventsApiResource();
        setField(resource, "repository", repository);
        setField(resource, "ordersRepository", ordersRepository);
        setField(resource, "publicKeyPem",
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        setField(resource, "keyAlgorithm", "RSA");
    }

    @Test
    void checkInUsesEventSessionAndSeatIdentifiers() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Date checkedInAt = new Date();
        TicketingSeat seat = new TicketingSeat()
                .id(seatId)
                .sessionId(sessionId)
                .status(TicketingSeat.StatusEnum.CHECKED_IN)
                .checkedInTimestamp(checkedInAt);
        affectedRows = 1;
        loadedSeat = seat;

        Response response = resource.checkIn(token(eventId, sessionId, seatId),
                eventId.toString(), sessionId.toString());

        assertEquals(200, response.getStatus());
        assertEquals(checkedInAt, ((TicketingSeat) response.getEntity()).getCheckedInTimestamp());
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

    @Test
    void eventStatisticsReturnsCheckInCountsForExistingEvent() {
        UUID eventId = UUID.randomUUID();
        loadedEvent = new Event().id(eventId);
        loadedStatistics = new EventStatistics()
                .eventId(eventId)
                .totalSeats(100)
                .orderedSeats(25)
                .checkedInSeats(10)
                .uncheckedInSeats(15);

        Response response = resource.getEventStatistics(eventId);

        assertEquals(200, response.getStatus());
        EventStatistics statistics = (EventStatistics) response.getEntity();
        assertEquals(100, statistics.getTotalSeats());
        assertEquals(25, statistics.getOrderedSeats());
        assertEquals(10, statistics.getCheckedInSeats());
        assertEquals(15, statistics.getUncheckedInSeats());
    }

    @Test
    void eventOrdersReturnsOrdersForExistingEvent() {
        UUID eventId = UUID.randomUUID();
        loadedEvent = new Event().id(eventId);
        loadedOrders = List.of(new Order().id(UUID.randomUUID()).eventId(eventId));

        Response response = resource.getEventOrders(eventId);

        assertEquals(200, response.getStatus());
        assertEquals(loadedOrders, response.getEntity());
        assertEquals(eventId, loadedOrdersEventId);
        assertEquals(1, loadOrdersCalls);
    }

    @Test
    void eventOrdersReturnsEmptyListWhenExistingEventHasNoOrders() {
        UUID eventId = UUID.randomUUID();
        loadedEvent = new Event().id(eventId);
        loadedOrders = List.of();

        Response response = resource.getEventOrders(eventId);

        assertEquals(200, response.getStatus());
        assertEquals(List.of(), response.getEntity());
        assertEquals(eventId, loadedOrdersEventId);
        assertEquals(1, loadOrdersCalls);
    }

    @Test
    void eventOrdersReturnsNotFoundWhenEventDoesNotExist() {
        UUID eventId = UUID.randomUUID();

        Response response = resource.getEventOrders(eventId);

        assertEquals(404, response.getStatus());
        assertEquals(0, loadOrdersCalls);
    }

    @Test
    void sessionStatisticsReturnsCountsForExistingSession() {
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        loadedSession = new Session().id(sessionId).eventId(eventId);
        loadedSessionStatistics = new SessionStatistics()
                .eventId(eventId)
                .sessionId(sessionId)
                .totalSeats(100)
                .orderedSeats(60)
                .checkedInSeats(20)
                .uncheckedInSeats(40);

        Response response = resource.getSessionStatistics(eventId, sessionId);

        assertEquals(200, response.getStatus());
        assertEquals(loadedSessionStatistics, response.getEntity());
    }

    @Test
    void sessionStatisticsReturnsNotFoundWhenSessionDoesNotExist() {
        Response response = resource.getSessionStatistics(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(404, response.getStatus());
    }

    @Test
    void sessionAreaSeatsReturnsSeatsForExistingSessionArea() {
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        loadedSession = new Session().id(sessionId).eventId(eventId);
        loadedArea = new Area().id(areaId);
        loadedSeats = List.of(new TicketingSeat().id(UUID.randomUUID()).status(TicketingSeat.StatusEnum.CHECKED_IN));

        Response response = resource.getAllSeatsInAreaOfSession(eventId, sessionId, areaId);

        assertEquals(200, response.getStatus());
        assertEquals(loadedSeats, response.getEntity());
        assertEquals(eventId, loadedOrdersEventId);
        assertEquals(sessionId, loadedSeatsSessionId);
        assertEquals(areaId, loadedSeatsAreaId);
    }

    @Test
    void sessionAreaSeatsReturnsNotFoundWhenAreaDoesNotExist() {
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        loadedSession = new Session().id(sessionId).eventId(eventId);

        Response response = resource.getAllSeatsInAreaOfSession(eventId, sessionId, UUID.randomUUID());

        assertEquals(404, response.getStatus());
    }

    @Test
    void sessionSeatStatusUsesOpenBookedAndCheckedInWireValues() {
        assertEquals(TicketingSeat.StatusEnum.OPEN, TicketingSeat.StatusEnum.fromValue("open"));
        assertEquals(TicketingSeat.StatusEnum.BOOKED, TicketingSeat.StatusEnum.fromValue("booked"));
        assertEquals(TicketingSeat.StatusEnum.CHECKED_IN, TicketingSeat.StatusEnum.fromValue("checkedIn"));
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
