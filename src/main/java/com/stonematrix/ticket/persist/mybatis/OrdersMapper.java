package com.stonematrix.ticket.persist.mybatis;

import com.stonematrix.ticket.api.model.Order;
import org.apache.ibatis.annotations.Mapper;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OrdersMapper {
    boolean isUserOrderExist(String userId, UUID orderId);

    void saveNewOrder(Order order);

    List<Order> loadOrders(String userId, Date startTime, Date endTime);

    List<Order> loadOrders(Date startTime, Date endTime);

    List<Order> loadOrders(String userId);

    List<Order> loadOrders();

    Order loadOrder(UUID orderId);

    void updateOrderPayAmount(UUID orderId, Integer paidAmount);

    void deleteOrder(UUID orderId);

    void updateOrderMetadata(Order order);
}
