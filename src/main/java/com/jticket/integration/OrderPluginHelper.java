package com.jticket.integration;

import com.jticket.persist.OrdersRepository;
import com.jticket.api.model.Order;
import com.jticket.persist.EventsRepository;
import jakarta.inject.Inject;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;


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
        Map<String, Object> eventMetadata = eventsRepository.loadEvent(order.getEventId()).getMetadata();
        String eventId = order.getEventId().toString();

        for (OrderPlugin plugin: orderPlugins) {
            try {
                if (plugin.matches(eventId, userId, eventMetadata))
                    return plugin.beforePlaceOrder(order);
            } catch (OrderPluginException e) {
                throw new SQLException(e);
            }
        }

        return order;
    }

    public void beforePayOrder(Order order, Integer payAmount) throws SQLException {
        if (orderPlugins.isEmpty()) return;

        Map<String, Object> eventMetadata = eventsRepository.loadEvent(order.getEventId()).getMetadata();
        String eventId = order.getEventId().toString();

        for (OrderPlugin plugin: orderPlugins) {
            try {
                if (plugin.matches(eventId, order.getUserId(), eventMetadata)) {
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

        Map<String, Object> eventMetadata = eventsRepository.loadEvent(order.getEventId()).getMetadata();
        String eventId = order.getEventId().toString();

        for (OrderPlugin plugin: orderPlugins) {
            try {
                if (plugin.matches(eventId, order.getUserId(), eventMetadata)) {
                    plugin.beforeCancelOrder(order);
                    return;
                }
            } catch (OrderPluginException e) {
                throw new SQLException(e);
            }
        }
    }
}
