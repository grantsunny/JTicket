package com.jticket.persist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jticket.JTicketApplication;
import com.jticket.api.model.TicketingSeat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = JTicketApplication.class, properties = {
        "spring.datasource.url=jdbc:derby:memory:jticket-statistics-regression;user=TKT;create=true",
        "spring.datasource.driver-class-name=org.apache.derby.iapi.jdbc.AutoloadedDriver"})
@ActiveProfiles({"dev", "test"})
class SeatStatusStatisticsTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventsRepository events;

    @Test
    void countsAllPhysicalSeatsAndKeepsSessionSpecificCheckInState() throws Exception {
        UUID venue = UUID.randomUUID();
        UUID area = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        UUID booked = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();
        UUID open = UUID.randomUUID();
        jdbc.update("INSERT INTO TKT.Venues(id,name) VALUES (?,?)", venue.toString(), "Statistics venue");
        jdbc.update("INSERT INTO TKT.Areas(id,venueId,name) VALUES (?,?,?)",
                area.toString(), venue.toString(), "Main");
        jdbc.update("INSERT INTO TKT.Events(id,venueId,name) VALUES (?,?,?)",
                event.toString(), venue.toString(), "Statistics event");
        jdbc.update("INSERT INTO TKT.Sessions(id,eventId,name,startTime,endTime) "
                + "VALUES (?,?,?,TIMESTAMP('2031-01-01 10:00:00'),TIMESTAMP('2031-01-01 12:00:00'))",
                first.toString(), event.toString(), "First");
        jdbc.update("INSERT INTO TKT.Sessions(id,eventId,name,startTime,endTime) "
                + "VALUES (?,?,?,TIMESTAMP('2031-01-02 10:00:00'),TIMESTAMP('2031-01-02 12:00:00'))",
                second.toString(), event.toString(), "Second");
        UUID[] seats = {booked, blocked, open};
        for (int i = 0; i < seats.length; i++) {
            jdbc.update("INSERT INTO TKT.Seats(id,areaId,row,col,available) VALUES (?,?,0,?,?)",
                    seats[i].toString(), area.toString(), i, i != 1);
        }

        // No prices: statistics must still include unavailable and unpriced seats.
        jdbc.update("INSERT INTO TKT.Orders(id,eventId,sessionId,userId) VALUES (?,?,?,?)",
                order.toString(), event.toString(), first.toString(), "customer");
        jdbc.update("INSERT INTO TKT.OrderSeats(orderId,eventId,sessionId,seatId) VALUES (?,?,?,?)",
                order.toString(), event.toString(), first.toString(), booked.toString());
        var beforeCheckIn = events.loadSessionStatistics(event, first);
        assertThat(beforeCheckIn.getTotalSeats()).isEqualTo(3);
        assertThat(beforeCheckIn.getUncheckedInSeats()).isEqualTo(1);
        assertThat(events.checkInSeat(event, first, booked)).isEqualTo(1);
        assertThat(events.checkInSeat(event, first, booked)).isZero();
        var stats = events.loadSessionStatistics(event, first);
        assertThat(stats.getOrderedSeats()).isEqualTo(1);
        assertThat(stats.getCheckedInSeats()).isEqualTo(1);
        assertThat(stats.getUncheckedInSeats()).isZero();
        var untouched = events.loadSessionStatistics(event, second);
        assertThat(untouched.getTotalSeats()).isEqualTo(3);
        assertThat(untouched.getOrderedSeats()).isZero();
        Map<UUID, TicketingSeat> statuses = events.loadSeatsInAreaOfSession(event, first, area).stream()
                .collect(Collectors.toMap(TicketingSeat::getId, Function.identity()));
        assertThat(statuses.get(booked).getStatus()).isEqualTo(TicketingSeat.StatusEnum.CHECKED_IN);
        assertThat(statuses.get(booked).getCheckedInTimestamp()).isNotNull();
        assertThat(statuses.get(booked).getOrderId()).isEqualTo(order);
        assertThat(statuses.get(blocked).getAvailable()).isFalse();
        assertThat(statuses.get(open).getAvailable()).isFalse();
        var secondSeat = events.loadSeatInSession(event, second, booked);
        assertThat(secondSeat.getStatus()).isEqualTo(TicketingSeat.StatusEnum.OPEN);
        assertThat(secondSeat.getCheckedInTimestamp()).isNull();
        assertThat(secondSeat.getOrderId()).isNull();
    }
}
