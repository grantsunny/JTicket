package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.OrdersApi;
import com.stonematrix.ticket.api.model.Order;
import com.stonematrix.ticket.api.model.Payment;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public class OrdersApiResource implements OrdersApi {

    @Context
    private UriInfo uriInfo;

    @Override
    public void cancelOrder(UUID orderId) {

    }

    @Override
    public Order createOrder(Order order) {
        return null;
    }

    @Override
    public List<Order> getAllOrders(Date startTime, Date endTime) {
        return null;
    }

    @Override
    public Order getArchivedOrder(UUID orderId) {
        return null;
    }

    @Override
    public Order getOrder(UUID orderId) {
        return null;
    }

    @Override
    public void payOrder(UUID orderId, Payment payment) {

    }
}
