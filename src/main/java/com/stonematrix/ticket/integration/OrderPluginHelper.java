package com.stonematrix.ticket.integration;

import com.stonematrix.ticket.api.model.Order;
import com.stonematrix.ticket.persist.EventsRepository;
import com.stonematrix.ticket.persist.OrdersRepository;
import jakarta.inject.Inject;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;


@Component
public class OrderPluginHelper {

    @Inject
    private EventsRepository eventsRepository;

    @Inject
    private OrdersRepository ordersRepository;

    @Inject
    private List<OrderPlugin> orderPlugins;

    public Order beforePlaceOrder(String userId, Order order) throws SQLException {

        if (orderPlugins.isEmpty()) return order;
        for (OrderPlugin plugin: orderPlugins) {
            try {
                if (plugin.matches(
                        order.getEventId().toString(),
                        userId,
                        eventsRepository.loadEvent(order.getEventId()).getMetadata()))
                    return plugin.beforePlaceOrder(order);
            } catch (OrderPluginException e) {
                throw new SQLException(e);
            }
        }

        return order;
    }

    public void beforePayOrder(String userId, String orderId, Integer payAmount) throws SQLException {
        if (orderPlugins.isEmpty()) return;
        Order order = ordersRepository.loadOrder(UUID.fromString(orderId));

        for (OrderPlugin plugin: orderPlugins) {
            try {
                if (plugin.matches(
                        order.getEventId().toString(),
                        userId,
                        eventsRepository.loadEvent(order.getEventId()).getMetadata())) {
                    ordersRepository.updateOrderMetadata(plugin.beforePayOrder(order, payAmount));
                    return;
                }
            } catch (OrderPluginException e) {
                throw new SQLException(e);
            }
        }
    }

    public void beforeCancelOrder(String userId, String orderId) throws SQLException {
        if (orderPlugins.isEmpty()) return;
        Order order = ordersRepository.loadOrder(UUID.fromString(orderId));

        for (OrderPlugin plugin: orderPlugins) {
            try {
                if (plugin.matches(
                        order.getEventId().toString(),
                        userId,
                        eventsRepository.loadEvent(order.getEventId()).getMetadata())) {
                    plugin.beforeCancelOrder(order);
                    return;
                }
            } catch (OrderPluginException e) {
                throw new SQLException(e);
            }
        }
    }
}
