package com.stonematrix.ticket.persist.mybatis;

import com.stonematrix.ticket.api.model.Order;
import com.stonematrix.ticket.api.model.OrderSeatsInner;
import com.stonematrix.ticket.persist.PersistenceException;
import com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler;
import org.apache.ibatis.annotations.*;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OrdersMapper {
    @Select("SELECT CASE WHEN (COUNT(*) > 0) THEN TRUE ELSE FALSE END FROM TKT.ORDERS WHERE USERID = #{userId} AND ID = #{orderId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    boolean isUserOrderExist(@Param("userId") String userId, @Param("orderId") UUID orderId);

    @Select("SELECT ORDERID, EVENTID, SEATID, METADATA FROM TKT.ORDERSEATS WHERE ORDERID = #{orderId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<OrderSeatsInner> _loadOrderSeats(@Param("orderId") UUID orderId);

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, (PAIDAMOUNT * 100) AS PAIDAMOUNT, METADATA FROM TKT.ORDERS " +
            "WHERE USERID = #{userId} AND TIMESTAMP BETWEEN #{startTime} AND #{endTime}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Order> _loadOrdersByUserIdAndDateRange(@Param("userId") String userId, @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    @Transactional
    default List<Order> loadOrders(String userId, Date startTime, Date endTime) {
        List<Order> orders = _loadOrdersByUserIdAndDateRange(userId, startTime, endTime);
        for (Order order: orders) {
            UUID orderId = order.getId();
            order.setSeats(_loadOrderSeats(orderId));
        }
        return orders;
    }

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, (PAIDAMOUNT * 100) AS PAIDAMOUNT, METADATA FROM TKT.ORDERS " +
            "WHERE TIMESTAMP BETWEEN #{startTime} AND #{endTime}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Order> _loadOrdersByDateRange(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    @Transactional
    default List<Order> loadOrders(Date startTime, Date endTime) {
        List<Order> orders = _loadOrdersByDateRange(startTime, endTime);
        for (Order order: orders) {
            UUID orderId = order.getId();
            order.setSeats(_loadOrderSeats(orderId));
        }
        return orders;
    }

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, (PAIDAMOUNT * 100) AS PAIDAMOUNT, METADATA FROM TKT.ORDERS " +
            "WHERE USERID = #{userId} ")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Order> _loadOrdersByUserId(@Param("userId") String userId);

    @Transactional
    default List<Order> loadOrders(String userId) {
        List<Order> orders = _loadOrdersByUserId(userId);
        for (Order order: orders) {
            UUID orderId = order.getId();
            order.setSeats(_loadOrderSeats(orderId));
        }
        return orders;
    }

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, (PAIDAMOUNT * 100) AS PAIDAMOUNT, METADATA FROM TKT.ORDERS")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Order> _loadOrders();

    @Transactional
    default List<Order> loadOrders() {
        List<Order> orders = _loadOrders();
        for (Order order: orders) {
            UUID orderId = order.getId();
            order.setSeats(_loadOrderSeats(orderId));
        }
        return orders;
    }

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, (PAIDAMOUNT * 100) AS PAIDAMOUNT, METADATA FROM TKT.ORDERS " +
            "WHERE ID = #{orderId} ")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Order _loadOrder(@Param("orderId") UUID orderId);

    @Transactional
    default Order loadOrder(UUID orderId) {
        Order order = _loadOrder(orderId);
        order.setSeats(_loadOrderSeats(orderId));
        return order;
    }

    @Insert("<script>INSERT INTO TKT.ORDERSEATS(ORDERID, EVENTID, SEATID, METADATA) VALUES " +
            "<foreach collection='seats' item='seat' separator=','> " +
            "(#{orderId}, #{eventId}, #{seat.seatId}, " +
            "#{seat.metadata, typeHandler=com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler}) " +
            "</foreach>" +
            "</script>")
    int _saveOrderSeats(UUID orderId, UUID eventId, List<OrderSeatsInner> seats);

    @Insert("INSERT INTO TKT.ORDERS(ID, EVENTID, USERID, TIMESTAMP, METADATA) " +
            "VALUES (#{order.Id}, #{order.eventId}, #{order.userId}, CURRENT_TIMESTAMP, " +
            "#{order.metadata, typeHandler=com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler})")
    int _saveNewOrder(Order order);

    @Transactional
    @ExecutorType(org.apache.ibatis.session.ExecutorType.BATCH)
    default void saveNewOrder(Order order) throws PersistenceException {
        if (_saveNewOrder(order) < 1)
            throw new PersistenceException(
                    new SQLException("_saveNewOrder not successfully performed", "304")
            );
        if (_saveOrderSeats(order.getId(), order.getEventId(), order.getSeats()) < order.getSeats().size())
            throw new PersistenceException(
                    new SQLException("_saveOrderSeats not successfully performed: affected rows less than expected", "304")
            );
    }

    @Update("UPDATE TKT.ORDERS SET PAIDAMOUNT = PAIDAMOUNT + (#{paidAmount} / 100.0) WHERE ID = #{orderId}")
    void updateOrderPayAmount(@Param("orderId") UUID orderId, @Param("paidAmount") Integer paidAmount);

    @Update("UPDATE TKT.ORDERS SET METADATA = #{order.metadata, typeHandler=com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler} WHERE ID = #{order.order}")
    void _updateOrderMetadata(@Param("order") Order order);

    @Update("UPDATE TKT.ORDERSEATS SET METADATA = " +
            "#{orderSeat.metadata, typeHandler=com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler} WHERE ORDERID = #{orderId} AND EVENTID = #{eventId} AND SEATID = #{orderSeat.seatId}")
    void _updateOrderSeatsMetadata(@Param("orderId") UUID orderId, @Param("eventId") UUID eventId, @Param("orderSeat") OrderSeatsInner orderSeat);

    @Transactional
    @ExecutorType(org.apache.ibatis.session.ExecutorType.BATCH)
    default void updateOrderMetadata(Order order) {
        _updateOrderMetadata(order);
        for (OrderSeatsInner orderSeat: order.getSeats()) {
            _updateOrderSeatsMetadata(order.getId(), order.getEventId(), orderSeat);
        }
    }

    @Delete("DELETE FROM TKT.ORDERSEATS WHERE ORDERID = #{orderId}")
    void _deleteOrderSeats(@Param("orderId") UUID orderId);

    @Delete("DELETE FROM TKT.ORDERS WHERE ID = #{orderId}")
    void _deleteOrder(@Param("orderId") UUID orderId);

    @Transactional
    @ExecutorType(org.apache.ibatis.session.ExecutorType.BATCH)
    default void deleteOrder(UUID orderId) {
        _deleteOrderSeats(orderId);
        _deleteOrder(orderId);
    }
}
