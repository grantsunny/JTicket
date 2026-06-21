package com.jticket.persist.mybatis;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.transaction.annotation.Transactional;

import com.jticket.api.model.Order;
import com.jticket.api.model.Seat;
import com.jticket.persist.OrdersRepository.PaymentResult;
import com.jticket.persist.PersistenceException;
import com.jticket.persist.mybatis.handlers.MetadataHandler;

@Mapper
public interface OrdersMapper {
    @Select("SELECT CASE WHEN (COUNT(*) > 0) THEN TRUE ELSE FALSE END FROM ORDERS WHERE USERID = #{userId} AND ID = #{orderId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    boolean isUserOrderExist(@Param("userId") String userId, @Param("orderId") UUID orderId);
    
    @Select("SELECT SKU.SEATID AS ID, SKU.AREAID, SKU.VENUEID, SKU.ROW, SKU.COL, SKU.AVAILABLE, "
            + "ORDERSEATS.checkedInTimestamp, ORDERSEATS.METADATA, SKU.PRICE, SKU.PRICENAME FROM ${SKU} "
            + "INNER JOIN ORDERSEATS ON ORDERSEATS.ORDERID = #{orderId} "
            + "AND ORDERSEATS.EVENTID = SKU.EVENTID "
            + "AND ORDERSEATS.SESSIONID = SKU.SESSIONID "
            + "AND ORDERSEATS.SEATID = SKU.SEATID")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Seat> _loadOrderSeats(@Param("orderId") UUID orderId);

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM ORDERS " +
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

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM ORDERS " +
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

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM ORDERS " +
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

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM ORDERS")
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

    @Select("SELECT ID, EVENTID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM ORDERS " +
            "WHERE ID = #{orderId} ")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Order _loadOrder(@Param("orderId") UUID orderId);

    @Transactional
    default Order loadOrder(UUID orderId) {
        Order order = _loadOrder(orderId);
        if (order != null)
            order.setSeats(_loadOrderSeats(orderId));
        return order;
    }

    @Insert("<script>INSERT INTO ORDERSEATS(ORDERID, EVENTID, SESSIONID, SEATID, METADATA) VALUES " +
            "<foreach collection='seats' item='seat' separator=','> " +
            "(#{orderId}, #{eventId}, #{sessionId}, #{seat.seatId}, " +
            "#{seat.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler}) " +
            "</foreach>" +
            "</script>")
    int _saveOrderSeats(UUID orderId, UUID eventId, UUID sessionId, List<Seat> seats);

    @Insert("INSERT INTO ORDERS(ID, EVENTID, SESSIONID, USERID, TIMESTAMP, METADATA) " +
            "VALUES (#{order.Id}, #{order.eventId}, #{order.sessionId}, #{order.userId}, CURRENT_TIMESTAMP, " +
            "#{order.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler})")
    int _saveNewOrder(Order order);

    @Transactional
    @ExecutorType(org.apache.ibatis.session.ExecutorType.BATCH)
    default void saveNewOrder(Order order) throws PersistenceException {
        if (_saveNewOrder(order) < 1)
            throw new PersistenceException(
                    new SQLException("_saveNewOrder not successfully performed", "304")
            );
        if (_saveOrderSeats(order.getId(), order.getEventId(), order.getSessionId(), order.getSeats()) < order.getSeats().size())
            throw new PersistenceException(
                    new SQLException("_saveOrderSeats not successfully performed: affected rows less than expected", "304")
            );
    }

    @Update("UPDATE ORDERS SET PAYMENTCHANNEL = #{paymentChannel}, PAYMENTTRANSACTIONID = #{paymentTransactionId}, " +
            "PAYMENTAMOUNT = #{paymentAmount}, PAYMENTTIMESTAMP = CURRENT_TIMESTAMP " +
            "WHERE ID = #{orderId} AND PAYMENTTRANSACTIONID IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM ORDERS WHERE PAYMENTTRANSACTIONID = #{paymentTransactionId} AND ID <> #{orderId})")
    int _applyOrderPayment(@Param("orderId") UUID orderId,
                           @Param("paymentChannel") String paymentChannel,
                           @Param("paymentTransactionId") String paymentTransactionId,
                           @Param("paymentAmount") Integer paymentAmount);

    @Select("SELECT CASE " +
            "WHEN COUNT(*) = 0 THEN 'ORDER_NOT_FOUND' " +
            "WHEN MAX(PAYMENTTRANSACTIONID) = #{paymentTransactionId} " +
            "AND MAX(PAYMENTCHANNEL) = #{paymentChannel} " +
            "AND MAX(PAYMENTAMOUNT) = #{paymentAmount} THEN 'IDEMPOTENT_RETRY' " +
            "ELSE 'CONFLICT' END " +
            "FROM ORDERS WHERE ID = #{orderId}")
    PaymentResult _classifyOrderPayment(@Param("orderId") UUID orderId,
                                         @Param("paymentChannel") String paymentChannel,
                                         @Param("paymentTransactionId") String paymentTransactionId,
                                         @Param("paymentAmount") Integer paymentAmount);

    @Transactional
    default PaymentResult updateOrderPayment(UUID orderId, String paymentChannel, String paymentTransactionId, Integer paymentAmount) {
        if (_applyOrderPayment(orderId, paymentChannel, paymentTransactionId, paymentAmount) > 0)
            return PaymentResult.SUCCESS;

        return _classifyOrderPayment(orderId, paymentChannel, paymentTransactionId, paymentAmount);
    }

    @Update("UPDATE ORDERS SET METADATA = #{order.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler} WHERE ID = #{order.id}")
    void _updateOrderMetadata(@Param("order") Order order);

    @Update("UPDATE ORDERSEATS SET METADATA = " +
            "#{seat.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler} WHERE ORDERID = #{orderId} AND EVENTID = #{eventId} AND SEATID = #{seat.id}")
    void _updateOrderSeatsMetadata(@Param("orderId") UUID orderId, @Param("eventId") UUID eventId, @Param("seat") Seat seat);

    
    
    
    
    
    @Transactional
    @ExecutorType(org.apache.ibatis.session.ExecutorType.BATCH)
    default void updateOrderMetadata(Order order) {
        _updateOrderMetadata(order);
        for (Seat seat: order.getSeats()) {
            _updateOrderSeatsMetadata(order.getId(), order.getEventId(), seat);
        }
    }

    @Delete("DELETE FROM ORDERSEATS WHERE ORDERID = #{orderId}")
    void _deleteOrderSeats(@Param("orderId") UUID orderId);

    @Delete("DELETE FROM ORDERS WHERE ID = #{orderId}")
    void _deleteOrder(@Param("orderId") UUID orderId);

    @Transactional
    @ExecutorType(org.apache.ibatis.session.ExecutorType.BATCH)
    default void deleteOrder(UUID orderId) {
        _deleteOrderSeats(orderId);
        _deleteOrder(orderId);
    }
}
