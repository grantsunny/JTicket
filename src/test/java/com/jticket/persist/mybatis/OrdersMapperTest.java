package com.jticket.persist.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.jticket.api.model.Order;
import com.jticket.api.model.TicketingSeat;
import org.junit.jupiter.api.Test;

class OrdersMapperTest {
    @Test
    void batchesSeatReadsForEveryOrderListFilterAndPreservesOwnership() {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 401; i++) {
            orders.add(new Order().id(UUID.randomUUID()));
        }
        AtomicInteger seatQueries = new AtomicInteger();
        OrdersMapper mapper = (OrdersMapper) Proxy.newProxyInstance(OrdersMapper.class.getClassLoader(),
                new Class<?>[]{OrdersMapper.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    if (method.getName().startsWith("_loadOrders")) {
                        return orders;
                    }
                    if (method.getName().equals("_loadOrderSeatsByOrderIds")) {
                        seatQueries.incrementAndGet();
                        List<?> ids = (List<?>) args[0];
                        assertThat(ids.size()).isBetween(1, 200);
                        List<TicketingSeat> seats = new ArrayList<>();
                        for (Object id : ids) {
                            seats.add(new TicketingSeat().id(UUID.randomUUID()).orderId((UUID) id));
                        }
                        return seats;
                    }
                    throw new AssertionError("Unexpected query: " + method);
                });
        List<Supplier<List<Order>>> variants = List.of(mapper::loadOrders,
                () -> mapper.loadOrders("customer"), () -> mapper.loadOrders(UUID.randomUUID()),
                () -> mapper.loadOrders(new Date(0), new Date()),
                () -> mapper.loadOrders("customer", new Date(0), new Date()));
        for (Supplier<List<Order>> variant : variants) {
            seatQueries.set(0);
            List<Order> result = variant.get();
            assertThat(seatQueries.get()).isEqualTo(3);
            assertThat(result).containsExactlyElementsOf(orders);
            for (Order order : result) {
                assertThat(order.getSeats()).hasSize(1);
                assertThat(order.getSeats().getFirst().getOrderId()).isEqualTo(order.getId());
            }
        }
    }

    @Test
    void emptyOrderListsDoNotIssueInvalidEmptyInQueries() {
        OrdersMapper mapper = (OrdersMapper) Proxy.newProxyInstance(OrdersMapper.class.getClassLoader(),
                new Class<?>[]{OrdersMapper.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    if (method.getName().equals("_loadOrders")) {
                        return List.of();
                    }
                    throw new AssertionError("Unexpected query: " + method);
                });
        assertThat(mapper.loadOrders()).isEmpty();
    }
}
