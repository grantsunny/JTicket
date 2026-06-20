package com.jticket.integration;

import com.jticket.persist.OrdersRepository;
import com.jticket.api.model.Order;
import com.jticket.persist.EventsRepository;
import jakarta.inject.Inject;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;


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

    public void beforePayOrder(Order order, Integer payAmount) throws SQLException {
        if (orderPlugins.isEmpty()) return;

        for (OrderPlugin plugin: orderPlugins) {
            try {
                if (plugin.matches(
                        order.getEventId().toString(),
                        order.getUserId(),
                        eventsRepository.loadEvent(order.getEventId()).getMetadata())) {
                    ordersRepository.updateOrderMetadata(plugin.beforePayOrder(order, payAmount));
                    return;
                }
            } catch (OrderPluginException e) {
                throw new SQLException(e);
            }
        }
    }

    public void beforeCancelOrder(Order order) throws SQLException {
        if (orderPlugins.isEmpty()) return;

        for (OrderPlugin plugin: orderPlugins) {
            try {
                if (plugin.matches(
                        order.getEventId().toString(),
                        order.getUserId(),
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
