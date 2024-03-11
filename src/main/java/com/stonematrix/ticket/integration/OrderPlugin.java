package com.stonematrix.ticket.integration;

import com.stonematrix.ticket.api.model.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * In order to enable a plugin, simply implement this interface and register it to Spring by @Component.
 */
public interface OrderPlugin {

    @Component
    public static class DummyOrderPlugin implements OrderPlugin {
        @Override
        public boolean matches(String eventId, String userId, Map<String, Object> eventMetadata) throws OrderPluginException {
            return false;
        }

        @Override
        public Order beforePlaceOrder(Order order) throws OrderPluginException {
            return null;
        }

        @Override
        public Order beforePayOrder(Order order, Integer payAmount) throws OrderPluginException {
            return null;
        }

        @Override
        public void beforeCancelOrder(Order order) throws OrderPluginException {

        }
    }



    boolean matches(String eventId, String userId, Map<String, Object> eventMetadata) throws OrderPluginException;

    Order beforePlaceOrder(Order order) throws OrderPluginException;

    Order beforePayOrder(Order order, Integer payAmount) throws OrderPluginException;

    void beforeCancelOrder(Order order) throws OrderPluginException;
}
