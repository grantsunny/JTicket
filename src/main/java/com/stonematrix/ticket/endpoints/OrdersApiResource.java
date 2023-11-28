package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.OrdersApi;
import com.stonematrix.ticket.api.model.Order;
import com.stonematrix.ticket.api.model.Payment;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.Date;
import java.util.UUID;

public class OrdersApiResource implements OrdersApi {

    @Context
    private UriInfo uriInfo;

    @Override
    public Response cancelOrder(UUID orderId) {

        return null;
    }

    @Override
    public Response createOrder(Order order) {
        return null;
    }

    @Override
    public Response getAllOrders(Date startTime, Date endTime) {
        return null;
    }

    @Override
    public Response getArchivedOrder(UUID orderId) {
        return null;
    }

    @Override
    public Response getOrder(UUID orderId) {
        return null;
    }

    @Override
    public Response payOrder(UUID orderId, Payment payment) {

        return null;
    }
}
