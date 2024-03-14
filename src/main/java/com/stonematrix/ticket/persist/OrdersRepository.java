package com.stonematrix.ticket.persist;

import com.stonematrix.ticket.api.model.Order;

import java.util.Date;
import java.util.List;
import java.util.UUID;

public interface OrdersRepository {
    boolean isUserOrderExist(String userId, UUID orderId) throws PersistenceException;
    void saveNewOrder(Order order) throws PersistenceException;
    List<Order> loadOrders(String userId, Date startTime, Date endTime) throws PersistenceException;
    List<Order> loadOrders(Date startTime, Date endTime) throws PersistenceException;
    List<Order> loadOrders(String userId) throws PersistenceException;
    List<Order> loadOrders() throws PersistenceException;
    Order loadOrder(UUID orderId) throws PersistenceException;
    void updateOrderPayAmount(UUID orderId, Integer paidAmount) throws PersistenceException;
    void deleteOrder(UUID orderId) throws PersistenceException;
    void updateOrderMetadata(Order order) throws PersistenceException;
}
