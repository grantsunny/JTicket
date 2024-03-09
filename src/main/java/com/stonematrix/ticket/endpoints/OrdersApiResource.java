package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.OrdersApi;
import com.stonematrix.ticket.api.model.Order;
import com.stonematrix.ticket.api.model.Payment;
import com.stonematrix.ticket.api.model.Price;
import com.stonematrix.ticket.persist.JdbcHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class OrdersApiResource implements OrdersApi {

    @Context
    private UriInfo uriInfo;

    @Inject
    private JdbcHelper jdbc;

    private void verifyOrderAndUser(String userId, UUID orderId) {
        try {
            if (!jdbc.isUserOrderExist(userId, orderId))
                throw new WebApplicationException("UserId is not the owner of specified order", Response.Status.NOT_FOUND);
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response createOrder(Order order, String xTicketUserId) {
        String userId = xTicketUserId;
        if (userId != null)
            order.setUserId(userId);
        else
            userId = order.getUserId();

        if (userId == null)
            throw new WebApplicationException("Cannot create order without user information", Response.Status.BAD_REQUEST);

        try {
            jdbc.saveNewOrder(order);
            UriBuilder uriBuilder =
                    uriInfo.getRequestUriBuilder().
                            path(String.valueOf(order.getId()));

            return Response.created(uriBuilder.build()).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getAllOrders(String xTicketUserId, Date startTime, Date endTime) {
        List<Order> orders;
        try {
            if (startTime != null && endTime != null)
                if (xTicketUserId != null)
                    orders = jdbc.loadOrders(xTicketUserId, startTime, endTime);
                else
                    orders = jdbc.loadOrders(startTime, endTime);
            else
            if (xTicketUserId != null)
                orders = jdbc.loadOrders(xTicketUserId);
            else
                orders = jdbc.loadOrders();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }

        if ((orders == null) || (orders.isEmpty()))
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        else
            return Response.ok(orders).build();
    }

    @Override
    public Response getOrder(UUID orderId, String xTicketUserId) {
        verifyOrderAndUser(xTicketUserId, orderId);
        try {
            Order order = jdbc.loadOrder(orderId);
            return Response.ok(order).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response payOrder(UUID orderId, Payment payment, String xTicketUserId) {
        verifyOrderAndUser(xTicketUserId, orderId);
        try {
            jdbc.updateOrderPayAmount(orderId, payment.getPaidAmount());
            return Response.accepted().build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response cancelOrder(UUID orderId, String xTicketUserId) {
        verifyOrderAndUser(xTicketUserId, orderId);
        try {
            jdbc.deleteOrder(orderId);
            return Response.noContent().build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }
}
