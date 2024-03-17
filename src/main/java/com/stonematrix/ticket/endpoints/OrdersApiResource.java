package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.OrdersApi;
import com.stonematrix.ticket.api.model.Order;
import com.stonematrix.ticket.api.model.Payment;
import com.stonematrix.ticket.integration.OrderPluginHelper;
import com.stonematrix.ticket.persist.OrdersRepository;
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
    private OrdersRepository repository;

    @Inject
    private OrderPluginHelper plugin;

    private void verifyOrderAndUser(String userId, UUID orderId) {
        try {
            if (!repository.isUserOrderExist(userId, orderId))
                throw new WebApplicationException("UserId is not the owner of specified order", Response.Status.NOT_FOUND);
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response createOrder(Order order, String userIdCookie, String userIdHeader) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        if (userId != null)
            order.setUserId(userId);
        else
            userId = order.getUserId();

        if (userId == null)
            throw new WebApplicationException("Cannot create order without user information", Response.Status.BAD_REQUEST);

        try {
            order.setId(UUID.randomUUID());
            order = plugin.beforePlaceOrder(userId, order);
            repository.saveNewOrder(order);
            UriBuilder uriBuilder =
                    uriInfo.getRequestUriBuilder().
                            path(String.valueOf(order.getId()));

            return Response.created(uriBuilder.build()).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getAllOrders(String userIdCookie, String userIdHeader, Date startTime, Date endTime) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        List<Order> orders;
        try {
            if (startTime != null && endTime != null)
                if (userId != null)
                    orders = repository.loadOrders(userId, startTime, endTime);
                else
                    orders = repository.loadOrders(startTime, endTime);
            else
            if (userId != null)
                orders = repository.loadOrders(userId);
            else
                orders = repository.loadOrders();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }

        if ((orders == null) || (orders.isEmpty()))
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        else
            return Response.ok(orders).build();
    }

    @Override
    public Response getOrder(UUID orderId, String userIdCookie, String userIdHeader) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        verifyOrderAndUser(userId, orderId);
        try {
            Order order = repository.loadOrder(orderId);
            return Response.ok(order).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response payOrder(UUID orderId, Payment payment, String userIdCookie, String userIdHeader) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        verifyOrderAndUser(userId, orderId);
        try {
            plugin.beforePayOrder(userId, orderId.toString(), payment.getPaidAmount());
            repository.updateOrderPayAmount(orderId, payment.getPaidAmount());
            return Response.accepted().build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response cancelOrder(UUID orderId, String userIdCookie, String userIdHeader) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        verifyOrderAndUser(userId, orderId);
        try {
            plugin.beforeCancelOrder(userId, orderId.toString());
            repository.deleteOrder(orderId);
            return Response.noContent().build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }
}
