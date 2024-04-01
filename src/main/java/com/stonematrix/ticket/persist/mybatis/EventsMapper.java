package com.stonematrix.ticket.persist.mybatis;

import com.stonematrix.ticket.api.model.*;
import com.stonematrix.ticket.persist.PersistenceException;
import com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler;
import org.apache.ibatis.annotations.*;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface EventsMapper {

    @Select("SELECT TKT.Prices.id, name, (price / 100.0) AS price, eventId FROM TKT.PricesDistribution " +
            "INNER JOIN TKT.Prices ON TKT.Prices.id = TKT.PricesDistribution.priceId " +
            "AND eventId = #{eventId} " +
            "AND seatId = #{seatId}")
    Price loadSeatLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId);

    @Select("SELECT id, name, (price / 100.0) AS price, eventId FROM TKT.Prices Where eventId = #{eventId}")
    List<Price> loadPrices(@Param("eventId") UUID eventId);

    @Select("SELECT seatId AS id, areaId, venueId, row, col, available, metadata, (price / 100.0) AS price, priceName, orderId FROM " +
            "TKT.SKU WHERE seatId = #{seatId} AND eventId = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Seat loadSeatInEvent(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId);

    @Select("SELECT TKT.Areas.id, TKT.Areas.venueId, TKT.Areas.name, TKT.Areas.metadata FROM TKT.Areas " +
            "INNER JOIN TKT.Venues ON TKT.Areas.venueId = TKT.Venues.id " +
            "INNER JOIN TKT.Events ON TKT.Events.venueId = TKT.Venues.id " +
            "AND TKT.Events.id = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Area> loadAllAreasInEvent(@Param("eventId") UUID eventId);

    @Select("SELECT id, areaId, venueId, row, col, available, metadata, (price / 100.0) AS price, priceName FROM "+
            "TKT.SEATSINEVENT WHERE eventId = #{eventId} AND areaId = #{areaId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Seat> loadSeatsInAreaOfEvent(@Param("eventId") UUID eventId, @Param("areaId") UUID areaId);

    @Select("SELECT TKT.Areas.id, TKT.Areas.venueId, TKT.Areas.name, TKT.Areas.metadata FROM TKT.Areas " +
            "INNER JOIN TKT.Venues ON TKT.Areas.venueId = TKT.Venues.id " +
            "INNER JOIN TKT.Events ON TKT.Events.venueId = TKT.Venues.id " +
            "AND TKT.Events.id = #{eventId} AND TKT.Areas.id = #{areaId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Area loadAreaInEvent(@Param("eventId") UUID eventId, @Param("areaId") UUID areaId);

    @Select("SELECT TKT.Prices.id, name, (price / 100.0) AS price, eventId FROM TKT.PricesDistribution " +
            "INNER JOIN TKT.Prices ON TKT.Prices.id = TKT.PricesDistribution.priceId " +
            "AND eventId = #{eventId} " +
            "AND areaId = #{areaId}")
    Price loadAreaLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("areaId") UUID areaId);

    @Select("SELECT TKT.Prices.id, TKT.Prices.name, (price / 100.0) AS price, TKT.Events.id AS eventId FROM TKT.PricesDistribution " +
            "INNER JOIN TKT.Prices ON TKT.Prices.id = TKT.PricesDistribution.priceId " +
            "INNER JOIN TKT.Events ON TKT.Events.venueId = TKT.PricesDistribution.venueId " +
            "AND TKT.Events.id = TKT.Prices.eventId " +
            "AND TKT.Events.id = #{eventId}")
    Price loadDefaultPricingOfEvent(@Param("eventId") UUID eventId);

    @Select("SELECT id, eventId, name, (price / 100.0) AS price FROM TKT.Prices WHERE id = #{priceId} AND eventId = #{eventId}")
    Price loadPriceOfEventById(@Param("eventId") UUID eventId, @Param("priceId") UUID priceId);

    @Select("SELECT id, venueId, name, metadata FROM TKT.Events WHERE id = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Event loadEvent(@Param("eventId") UUID eventId);

    @Select("SELECT id, venueId, name, metadata FROM TKT.Events WHERE venueId = #{venueId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Event> loadEventsByVenue(@Param("venueId") String venueId);

    @Select("SELECT id, venueId, name, metadata FROM TKT.Events")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Event> loadAllEvents();

    @Select("SELECT venueId AS id, TKT.Venues.name, TKT.Venues.metadata " +
            "FROM TKT.Events " +
            "INNER JOIN TKT.Venues ON venueId = TKT.Venues.id " +
            "AND TKT.Events.id = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Venue loadVenueByEvent(@Param("eventId") UUID eventId);

    @Select("SELECT svg FROM TKT.Events " +
            "INNER JOIN TKT.Venues ON venueId = TKT.Venues.id " +
            "AND TKT.Events.id = #{eventId}")
    File loadEventVenueSvg(@Param("eventId") UUID eventId);

    @Insert("INSERT INTO TKT.Prices (id, eventId, name, price) VALUES (#{price.id}, #{eventId}, #{price.name}, #{price.price} * 100)")
    int _saveTicketPriceOfEvent(@Param("eventId") UUID eventId, @Param("price") Price price);

    default void saveTicketPriceOfEvent(UUID eventId, Price price) throws PersistenceException {
        if (_saveTicketPriceOfEvent(eventId, price) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully added", "304")
            );
    }

    @Delete("DELETE FROM TKT.PricesDistribution WHERE priceId IN (SELECT id FROM TKT.Prices WHERE eventId = #{eventId})")
    void _deletePriceDistributionByEvent(@Param("eventId") UUID eventId);

    @Delete("DELETE FROM TKT.Prices WHERE eventId = #{eventId}")
    void _deletePriceByEvent(@Param("eventId") UUID eventId);

    @Delete("DELETE FROM TKT.Events WHERE id = #{eventId}")
    void _deleteEvent(@Param("eventId") UUID eventId);

    @Transactional
    default void deleteEvent(UUID eventId) {
        _deletePriceDistributionByEvent(eventId);
        _deletePriceByEvent(eventId);
        _deleteEvent(eventId);
    }

    @Delete("DELETE FROM TKT.Prices WHERE id = #{priceId} AND eventId = #{eventId}")
    int _deleteTicketPriceOfEvent(@Param("eventId") UUID eventId, @Param("priceId") UUID priceId);

    default void deleteTicketPriceOfEvent(UUID eventId, UUID priceId) throws PersistenceException {
        if (_deleteTicketPriceOfEvent(eventId, priceId) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully deleted", "304")
            );
    }

    @Update("UPDATE TKT.PricesDistribution " +
            "SET priceId = #{priceId} " +
            "WHERE priceId IN (SELECT id FROM TKT.Prices WHERE eventId = #{eventId}) " +
            "AND seatId IS NULL " +
            "AND areaId IS NULL " +
            "AND venueId IN (SELECT venueId FROM TKT.Events WHERE id = #{eventId}) ")
    int _updateDefaultPricingOfEvent(@Param("eventId") UUID eventId, @Param("priceId") UUID priceId);

    @Insert("INSERT INTO TKT.PricesDistribution (id, priceId, seatId, areaId, venueId) " +
            "SELECT #{newId}, #{priceId}, NULL, NULL, TKT.Events.venueId FROM TKT.Prices " +
            "INNER JOIN TKT.Events ON TKT.Events.id = TKT.Prices.eventId " +
            "AND TKT.Events.id = #{eventId}")
    int _saveDefaultPricingOfEvent(@Param("newId") UUID newId, @Param("eventId") UUID eventId, @Param("priceId") UUID priceId);

    @Transactional
    default void saveDefaultPricingOfEvent(UUID eventId, UUID priceId) throws PersistenceException {
        if (_updateDefaultPricingOfEvent(eventId, priceId) < 1)
            if (_saveDefaultPricingOfEvent(UUID.randomUUID(), eventId, priceId) < 1)
                throw new PersistenceException(
                        new SQLException("Not successfully saved", "304")
                );
    }

    @Update("UPDATE TKT.PricesDistribution " +
            "SET priceId = #{priceId} " +
            "WHERE priceId IN (SELECT TKT.Prices.id FROM TKT.Prices WHERE eventId = #{eventId}) " +
            "AND seatId = #{seatId} " +
            "AND areaId IS NULL " +
            "AND venueId IS NULL ")
    int _updateSeatLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId, @Param("priceId") UUID priceId);

    @Insert("INSERT INTO TKT.PricesDistribution (id, priceId, seatId, areaId, venueId) " +
            "VALUES (#{newId}, #{priceId}, #{seatId}, NULL, NULL)")
    int _saveSeatLevelPricingOfEvent(@Param("newId") UUID newId, @Param("eventId") UUID eventId, @Param("seatId") UUID seatId, @Param("priceId") UUID priceId);

    @Transactional
    default void saveSeatLevelPricingOfEvent(UUID eventId, UUID seatId, UUID priceId) throws PersistenceException {
        if (_updateSeatLevelPricingOfEvent(eventId, seatId, priceId) < 1)
            if (_saveSeatLevelPricingOfEvent(UUID.randomUUID(), eventId, seatId, priceId) < 1)
                throw new PersistenceException(
                        new SQLException("Not successfully saved", "304")
                );
    }

    @Update("UPDATE TKT.PricesDistribution " +
            "SET priceId = #{priceId} " +
            "WHERE priceId IN (SELECT TKT.Prices.id FROM TKT.Prices WHERE eventId = #{eventId}) " +
            "AND seatId IS NULL " +
            "AND areaId = #{areaId} " +
            "AND venueId IS NULL ")
    int _updateAreaLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("areaId") UUID areaId, @Param("priceId") UUID priceId);

    @Insert("INSERT INTO TKT.PricesDistribution (id, priceId, seatId, areaId, venueId) " +
            "VALUES (#{newId}, #{priceId}, NULL, #{areaId}, NULL)")
    int _saveAreaLevelPricingOfEvent(@Param("newId") UUID newId, @Param("eventId") UUID eventId, @Param("areaId") UUID areaId, @Param("priceId") UUID priceId);

    @Transactional
    default void saveAreaLevelPricingOfEvent(UUID eventId, UUID areaId, UUID priceId) throws PersistenceException{
        if (_updateAreaLevelPricingOfEvent(eventId, areaId, priceId) < 1)
            if (_saveAreaLevelPricingOfEvent(UUID.randomUUID(), eventId, areaId, priceId) < 1)
                throw new PersistenceException(
                        new SQLException("Not successfully saved", "304")
                );
    }

    @Insert("INSERT INTO TKT.Events (id, name, venueId, metadata) " +
            "VALUES (#{event.id}, #{event.name}, #{event.venueId}, " +
            "#{event.metadata, typeHandler=com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler})")
    void saveEvent(@Param("event") Event event);

    @Update("UPDATE TKT.Events SET venueId = #{venueId} WHERE id = #{eventId}")
    int _updateVenueOfEvent(@Param("eventId") UUID eventId, @Param("venueId") UUID venueId);

    default void updateVenueOfEvent(UUID eventId, UUID venueId) throws PersistenceException {
        if (_updateVenueOfEvent(eventId, venueId) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully updated", "304")
            );
    }

    @Update("UPDATE TKT.Events SET " +
            "venueId = #{event.venueId}, " +
            "name = #{event.name}, " +
            "metadata = #{event.metadata, typeHandler=com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler} " +
            "WHERE id = #{eventId}")
    int _updateEvent(@Param("eventId") UUID eventId, @Param("event") Event event);

    default void updateEvent(UUID eventId, Event event) throws PersistenceException {
        if (_updateEvent(eventId, event) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully updated", "304")
            );
    }

    @Select("SELECT priceId, seatId, areaId, venueId FROM TKT.PricesDistribution " +
            "INNER JOIN TKT.Prices ON TKT.Prices.id = TKT.PricesDistribution.priceId " +
            "AND TKT.Prices.eventId = #{eventId}")
    List<Map<String, Object>> _loadAllPricingOfEvent(@Param("eventId") UUID eventId);

    @Insert("<script>INSERT INTO TKT.PricesDistribution VALUES " +
            "<foreach collection='pricings' item='pricing' separator=','> " +
            "(#{pricing.ID, jdbcType=VARCHAR}, #{pricing.PRICEID, jdbcType=VARCHAR}, " +
            "#{pricing.SEATID, jdbcType=VARCHAR}, #{pricing.AREAID, jdbcType=VARCHAR}, " +
            "#{pricing.VENUEID, jdbcType=VARCHAR})" +
            "</foreach>" +
            "</script>")
    void _saveAllPricingOfEvent(@Param("pricings") List<Map<String, Object>> pricings);

    @Transactional
    @ExecutorType(org.apache.ibatis.session.ExecutorType.BATCH)
    default void saveEventAndCopyPrices(Event event, String copyFromEventId) throws PersistenceException {
        saveEvent(event);
        List<Price> prices = loadPrices(UUID.fromString(copyFromEventId));
        /* Key: priceCopyFrom, Value: new priceId */
        Map<UUID, UUID> priceIdMap = new HashMap<>();

        for (Price price: prices) {
            priceIdMap.put(price.getId(), UUID.randomUUID());
            price.setId(priceIdMap.get(price.getId()));
            saveTicketPriceOfEvent(event.getId(), price);
        }

        List<Map<String, Object>> pricings = _loadAllPricingOfEvent(UUID.fromString(copyFromEventId));
        for (Map<String, Object> pricing: pricings) {
            pricing.put("ID", UUID.randomUUID());
            pricing.put("PRICEID", priceIdMap.get(UUID.fromString(pricing.get("PRICEID").toString())));
        }

        _saveAllPricingOfEvent(pricings);
    }

    @Insert("INSERT INTO TKT.SESSIONS (id, name, eventId, startTime, endTime, metadata) " +
            "VALUES (#{session.id}, #{session.name}, #{eventId}, #{session.startTime}, #{session.endTime}, " +
            "#{session.metadata, typeHandler=com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler})")
    void saveSession(@Param("eventId") UUID eventId, @Param("session") Session session);

    @Update("UPDATE TKT.Sessions SET " +
            "name = #{session.name}, " +
            "startTime = #{session.startTime}, " +
            "endTime = #{session.endTime}, " +
            "metadata = #{session.metadata, typeHandler=com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler} " +
            "WHERE id = #{sessionId} AND eventId = #{eventId}")
    void updateSession(@Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId, @Param("session") Session session);

    @Delete("DELETE FROM TKT.Sessions WHERE id = #{id} AND eventId = #{eventid}")
    void deleteSession(@Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId);

    @Select("SELECT id, eventId, name, startTime, endTime, metadata FROM TKT.Sessions WHERE eventId = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Session> loadSessions(@Param("eventId") UUID eventId);

    @Select("SELECT id, eventId, name, startTime, endTime, metadata FROM TKT.Sessions WHERE id = #{sessionId} AND eventId = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Session loadSession(@Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId);
}
