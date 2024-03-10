package com.stonematrix.ticket.integration;

import com.stonematrix.ticket.api.model.Order;

import java.util.Map;

/**
 * In order to enable a plugin, simply implement this interface and register it to Spring by @Component.
 */
public interface OrderPlugin {

    boolean matches(String eventId, String userId, Map<String, Object> eventMetadata) throws OrderPluginException;

    Order beforePlaceOrder(Order order) throws OrderPluginException;

    Order beforePayOrder(Order order, Integer payAmount) throws OrderPluginException;

    void beforeCancelOrder(Order order) throws OrderPluginException;
}
