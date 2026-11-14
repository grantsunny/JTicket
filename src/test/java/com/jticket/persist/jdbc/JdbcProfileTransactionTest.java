package com.jticket.persist.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.BatchUpdateException;
import java.util.AbstractList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.jticket.JTicketApplication;
import com.jticket.api.model.Area;
import com.jticket.api.model.Order;
import com.jticket.api.model.Seat;
import com.jticket.api.model.TicketingSeat;
import com.jticket.persist.EventPoster;
import com.jticket.persist.EventsRepository;
import com.jticket.persist.OrdersRepository;
import com.jticket.persist.PersistenceException;
import com.jticket.persist.SeatsRepository;
import com.jticket.persist.VenuesRepository;
import com.jticket.persist.mybatis.MybatisPersistConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(classes = JTicketApplication.class, properties = {
        "spring.datasource.url=jdbc:derby:memory:jticket-jdbc-transactions;user=TKT;create=true",
        "spring.datasource.driver-class-name=org.apache.derby.iapi.jdbc.AutoloadedDriver",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.connection-timeout=1000"})
@ActiveProfiles({"dev", "jdbc", "test"})
class JdbcProfileTransactionTest {
    @Autowired private ApplicationContext context;
    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JdbcHelper helper;
    @Autowired private OrdersRepository orders;
    @Autowired private EventsRepository events;
    @Autowired private SeatsRepository seats;
    @Autowired private VenuesRepository venues;

    private UUID venueId;
    private UUID areaId;
    private UUID eventId;
    private UUID sessionId;
    private UUID firstSeat;
    private UUID secondSeat;

    @BeforeEach
    void createFixture() {
        venueId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        firstSeat = UUID.randomUUID();
        secondSeat = UUID.randomUUID();
        jdbc.update("INSERT INTO TKT.Venues(id,name) VALUES (?,?)", venueId.toString(), venueId.toString());
        jdbc.update("INSERT INTO TKT.Areas(id,venueId,name) VALUES (?,?,?)", areaId.toString(), venueId.toString(), "Main");
        jdbc.update("INSERT INTO TKT.Events(id,venueId,name) VALUES (?,?,?)", eventId.toString(), venueId.toString(), eventId.toString());
        jdbc.update("INSERT INTO TKT.Sessions(id,eventId,name,startTime,endTime) "
                + "VALUES (?,?,?,TIMESTAMP('2031-01-01 10:00:00'),TIMESTAMP('2031-01-01 12:00:00'))",
                sessionId.toString(), eventId.toString(), "Opening");
        jdbc.update("INSERT INTO TKT.Seats(id,areaId,row,col,available) VALUES (?,?,0,0,TRUE)", firstSeat.toString(), areaId.toString());
        jdbc.update("INSERT INTO TKT.Seats(id,areaId,row,col,available) VALUES (?,?,0,1,TRUE)", secondSeat.toString(), areaId.toString());
    }

    @Test
    void selectsJdbcRepositoriesAndCommitsThroughTheSpringProxy() throws Exception {
        assertThat(context.getBeansOfType(MybatisPersistConfiguration.class)).isEmpty();
        assertThat(context.getBeansOfType(OrdersRepository.class)).hasSize(1);
        assertThat(context.getBeansOfType(EventsRepository.class)).hasSize(1);
        assertThat(context.getBeansOfType(SeatsRepository.class)).hasSize(1);
        assertThat(context.getBeansOfType(VenuesRepository.class)).hasSize(1);
        assertThat(AopUtils.isCglibProxy(helper)).isTrue();

        Order order = newOrder(firstSeat);
        orders.saveNewOrder(order);

        assertThat(orders.loadOrders(eventId)).extracting(Order::getId).containsExactly(order.getId());
        assertThat(orders.loadOrder(order.getId()).getSeats()).extracting(TicketingSeat::getId).containsExactly(firstSeat);
        assertTransactionReleased();
    }

    @Test
    void rollsBackSqlSubclassAndEarlierSuccessfulBatchRowsThenRecovers() throws Exception {
        Order existing = newOrder(firstSeat);
        orders.saveNewOrder(existing);
        Order rejected = newOrder(secondSeat, firstSeat);

        assertThatThrownBy(() -> orders.saveNewOrder(rejected))
                .isInstanceOf(PersistenceException.class)
                .hasCauseInstanceOf(BatchUpdateException.class);

        assertTransactionReleased();
        assertThat(count("Orders", "id", rejected.getId())).isZero();
        assertThat(count("OrderSeats", "seatId", secondSeat)).isZero();
        assertThat(count("OrderSeats", "orderId", existing.getId())).isEqualTo(1);
        orders.saveNewOrder(newOrder(secondSeat));
        assertTransactionReleased();
    }

    @Test
    void rollsBackRuntimeFailureAfterOrderInsertThenRecovers() throws Exception {
        Order rejected = newOrder(firstSeat);
        rejected.setSeats(null);

        assertThatThrownBy(() -> orders.saveNewOrder(rejected)).isInstanceOf(NullPointerException.class);

        assertTransactionReleased();
        assertThat(count("Orders", "id", rejected.getId())).isZero();
        orders.saveNewOrder(newOrder(firstSeat));
        assertTransactionReleased();
    }

    @Test
    void rollsBackErrorAfterOrderInsertThenRecovers() throws Exception {
        AssertionError failure = new AssertionError("Seat iteration failed");
        Order rejected = newOrder(firstSeat);
        rejected.setSeats(new AbstractList<>() {
            @Override
            public TicketingSeat get(int index) {
                throw failure;
            }

            @Override
            public int size() {
                return 1;
            }
        });

        assertThatThrownBy(() -> orders.saveNewOrder(rejected)).isSameAs(failure);

        assertTransactionReleased();
        assertThat(count("Orders", "id", rejected.getId())).isZero();
        orders.saveNewOrder(newOrder(firstSeat));
        assertTransactionReleased();
    }

    @Test
    void retainsBookedSeatsWhenPaidOrderDeletionFails() throws Exception {
        Order order = newOrder(firstSeat);
        orders.saveNewOrder(order);
        orders.updateOrderPayment(order.getId(), "card", UUID.randomUUID().toString(), 100);

        assertThatThrownBy(() -> orders.deleteOrder(order.getId())).isInstanceOf(PersistenceException.class);

        assertTransactionReleased();
        assertThat(count("Orders", "id", order.getId())).isEqualTo(1);
        assertThat(count("OrderSeats", "orderId", order.getId())).isEqualTo(1);
        assertThat(orders.loadOrder(order.getId()).getSeats()).hasSize(1);
    }

    @Test
    void retainsExistingPosterWhenReplacementViolatesSqlConstraint() throws Exception {
        byte[] original = {1, 2, 3};
        events.saveEventPoster(eventId, new EventPoster("image/png", original));

        assertThatThrownBy(() -> events.saveEventPoster(eventId, new EventPoster("image/png", null)))
                .isInstanceOf(PersistenceException.class);

        assertTransactionReleased();
        assertThat(events.loadEventPoster(eventId).content()).containsExactly(original);
    }

    @Test
    void rollsBackEarlierAreasWhenALaterAreaViolatesAConstraint() {
        UUID rejectedArea = UUID.randomUUID();

        assertThatThrownBy(() -> venues.saveAreas(List.of(
                new Area().id(rejectedArea).venueId(venueId).name("Candidate"),
                new Area().id(UUID.randomUUID()).venueId(venueId).name("Main"))))
                .isInstanceOf(PersistenceException.class);

        assertTransactionReleased();
        assertThat(count("Areas", "id", rejectedArea)).isZero();
    }

    @Test
    void rollsBackEarlierSeatsWhenALaterSeatViolatesAConstraint() {
        UUID rejectedSeat = UUID.randomUUID();

        assertThatThrownBy(() -> seats.saveSeats(List.of(
                new Seat().id(rejectedSeat).areaId(areaId).row(1).col(0).available(true),
                new Seat().id(UUID.randomUUID()).areaId(areaId).row(0).col(0).available(true))))
                .isInstanceOf(PersistenceException.class);

        assertTransactionReleased();
        assertThat(count("Seats", "id", rejectedSeat)).isZero();
    }

    private Order newOrder(UUID... seatIds) {
        Order order = new Order().id(UUID.randomUUID()).eventId(eventId).sessionId(sessionId).userId("jdbc-review");
        for (UUID seatId : seatIds) {
            order.addSeatsItem(new TicketingSeat().id(seatId));
        }
        return order;
    }

    private int count(String table, String column, UUID id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM TKT." + table + " WHERE " + column + " = ?",
                Integer.class, id.toString());
    }

    private void assertTransactionReleased() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
    }
}
