package com.jticket.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jticket.api.model.Event;
import com.jticket.api.model.Order;
import com.jticket.persist.EventsRepository;
import com.jticket.persist.OrdersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderPluginHelperTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String USER_ID = "customer-sub";
    private static final Map<String, Object> EVENT_METADATA = Map.of("campaign", "spring");

    private OrderPluginHelper helper;
    private EventsRepository eventsRepository;
    private OrdersRepository ordersRepository;
    private RecordingOrderPlugin matchingPlugin;
    private RecordingOrderPlugin nonMatchingPlugin;

    @BeforeEach
    void setUp() throws Exception {
        helper = new OrderPluginHelper();
        eventsRepository = org.mockito.Mockito.mock(EventsRepository.class);
        ordersRepository = org.mockito.Mockito.mock(OrdersRepository.class);
        matchingPlugin = new RecordingOrderPlugin(true);
        nonMatchingPlugin = new RecordingOrderPlugin(false);

        ReflectionTestUtils.setField(helper, "eventsRepository", eventsRepository);
        ReflectionTestUtils.setField(helper, "ordersRepository", ordersRepository);
        ReflectionTestUtils.setField(helper, "orderPlugins", List.of(nonMatchingPlugin, matchingPlugin));

        when(eventsRepository.loadEvent(EVENT_ID)).thenReturn(new Event().id(EVENT_ID).metadata(EVENT_METADATA));
    }

    @Test
    void beforePlaceOrderUsesFirstMatchingPlugin() throws Exception {
        Order original = order();
        Order decorated = order().metadata(Map.of("plugin", "matched"));
        matchingPlugin.beforePlaceOrderResult = decorated;

        Order result = helper.beforePlaceOrder(USER_ID, original);

        assertThat(result).isSameAs(decorated);
        assertThat(nonMatchingPlugin.beforePlaceOrderCalls).isZero();
        assertThat(matchingPlugin.matchedUserId).isEqualTo(USER_ID);
        assertThat(matchingPlugin.matchedEventMetadata).isEqualTo(EVENT_METADATA);
        assertThat(matchingPlugin.beforePlaceOrderCalls).isEqualTo(1);
    }

    @Test
    void beforePayOrderUsesStoredOrderOwnerForPluginMatching() throws Exception {
        Order order = order();
        Order updated = order.metadata(Map.of("paid", true));
        matchingPlugin.beforePayOrderResult = updated;

        helper.beforePayOrder(order, 4500);

        assertThat(matchingPlugin.matchedUserId).isEqualTo(USER_ID);
        assertThat(matchingPlugin.beforePayOrderAmount).isEqualTo(4500);
        verify(ordersRepository).updateOrderMetadata(updated);
    }

    @Test
    void beforeCancelOrderInvokesFirstMatchingPluginOnly() throws Exception {
        Order order = order();

        helper.beforeCancelOrder(order);

        assertThat(nonMatchingPlugin.beforeCancelOrderCalls).isZero();
        assertThat(matchingPlugin.beforeCancelOrderCalls).isEqualTo(1);
    }

    @Test
    void noMatchingPluginLeavesOrderUnchangedAndSkipsMetadataUpdate() throws Exception {
        ReflectionTestUtils.setField(helper, "orderPlugins", List.of(nonMatchingPlugin));
        Order order = order();

        Order result = helper.beforePlaceOrder(USER_ID, order);
        helper.beforePayOrder(order, 4500);
        helper.beforeCancelOrder(order);

        assertThat(result).isSameAs(order);
        verify(ordersRepository, never()).updateOrderMetadata(org.mockito.Mockito.any());
    }

    @Test
    void pluginExceptionsAreWrappedAsSqlExceptions() {
        matchingPlugin.exception = new OrderPluginException();

        assertThatThrownBy(() -> helper.beforePlaceOrder(USER_ID, order()))
                .isInstanceOf(SQLException.class)
                .hasCause(matchingPlugin.exception);
    }

    private static Order order() {
        return new Order()
                .id(UUID.randomUUID())
                .eventId(EVENT_ID)
                .userId(USER_ID);
    }

    private static final class RecordingOrderPlugin implements OrderPlugin {

        private final boolean matches;
        private OrderPluginException exception;
        private String matchedUserId;
        private Map<String, Object> matchedEventMetadata;
        private Order beforePlaceOrderResult;
        private Order beforePayOrderResult;
        private Integer beforePayOrderAmount;
        private int beforePlaceOrderCalls;
        private int beforeCancelOrderCalls;

        private RecordingOrderPlugin(boolean matches) {
            this.matches = matches;
        }

        @Override
        public boolean matches(String eventId, String userId, Map<String, Object> eventMetadata)
                throws OrderPluginException {
            if (exception != null)
                throw exception;
            matchedUserId = userId;
            matchedEventMetadata = eventMetadata;
            return matches;
        }

        @Override
        public Order beforePlaceOrder(Order order) {
            beforePlaceOrderCalls++;
            return beforePlaceOrderResult == null ? order : beforePlaceOrderResult;
        }

        @Override
        public Order beforePayOrder(Order order, Integer payAmount) {
            beforePayOrderAmount = payAmount;
            return beforePayOrderResult == null ? order : beforePayOrderResult;
        }

        @Override
        public void beforeCancelOrder(Order order) {
            beforeCancelOrderCalls++;
        }
    }
}
