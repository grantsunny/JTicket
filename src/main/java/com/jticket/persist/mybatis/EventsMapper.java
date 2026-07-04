package com.jticket.persist.mybatis;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import com.jticket.api.model.Area;
import com.jticket.api.model.Event;
import com.jticket.api.model.EventStatistics;
import com.jticket.api.model.Price;
import com.jticket.api.model.Session;
import com.jticket.api.model.TicketingSeat;
import com.jticket.api.model.SessionStatistics;
import com.jticket.api.model.Venue;
import com.jticket.persist.EventPoster;
import com.jticket.persist.PersistenceException;
import com.jticket.persist.mybatis.handlers.MetadataHandler;
import com.jticket.persist.mybatis.handlers.TicketingSeatStatusHandler;

@Mapper
public interface EventsMapper {

    @Update("UPDATE OrderSeats SET checkedInTimestamp = CURRENT_TIMESTAMP " +
            "WHERE eventId = #{eventId} AND sessionId = #{sessionId} AND seatId = #{seatId} " +
            "AND checkedInTimestamp IS NULL")
	int checkInSeat(@Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId, @Param("seatId") UUID seatId);

    @Select("SELECT eventId, totalSeats, orderedSeats, checkedInSeats, uncheckedInSeats " +
            "FROM EventStatistics WHERE eventId = #{eventId}")
    EventStatistics loadEventStatistics(@Param("eventId") UUID eventId);

    @Select("SELECT eventId, sessionId, totalSeats, orderedSeats, checkedInSeats, uncheckedInSeats " +
            "FROM SessionStatistics WHERE eventId = #{eventId} AND sessionId = #{sessionId}")
    SessionStatistics loadSessionStatistics(@Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId);
	
    @Select("SELECT Prices.id, name, price, eventId FROM PricesDistribution " +
            "INNER JOIN Prices ON Prices.id = PricesDistribution.priceId " +
            "AND eventId = #{eventId} " +
            "AND seatId = #{seatId}")
    Price loadSeatLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId);

    @Select("SELECT id, name, price, eventId FROM Prices Where eventId = #{eventId}")
    List<Price> loadPrices(@Param("eventId") UUID eventId);

    @Select("SELECT id, areaId, venueId, row, col, available, metadata, price, priceName, sold FROM " +
            "${SEATSINEVENT} WHERE id = #{seatId} AND eventId = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    TicketingSeat loadSeatInEvent(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId);

    @Select("SELECT Areas.id, Areas.venueId, Areas.name, Areas.metadata FROM Areas " +
            "INNER JOIN Venues ON Areas.venueId = Venues.id " +
            "INNER JOIN Events ON Events.venueId = Venues.id " +
            "AND Events.id = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Area> loadAllAreasInEvent(@Param("eventId") UUID eventId);

    @Select("SELECT id, areaId, venueId, row, col, available, metadata, price, priceName, sold FROM "+
            "${SEATSINEVENT} WHERE eventId = #{eventId} AND areaId = #{areaId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<TicketingSeat> loadSeatsInAreaOfEvent(@Param("eventId") UUID eventId, @Param("areaId") UUID areaId);

    @Select("SELECT seatId AS id, sessionId, areaId, venueId, row, col, available, checkedInTimestamp, status, orderId, userId, " +
            "price, priceName, metadata FROM TKT.TicketingSeatStatus " +
            "WHERE eventId = #{eventId} AND sessionId = #{sessionId} AND areaId = #{areaId} ORDER BY row, col")
    @Results({
            @Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class),
            @Result(property = "status", column = "status", typeHandler = TicketingSeatStatusHandler.class)
    })
    List<TicketingSeat> loadSeatsInAreaOfSession(@Param("eventId") UUID eventId,
                                               @Param("sessionId") UUID sessionId,
                                               @Param("areaId") UUID areaId);

    @Select("SELECT seatId AS id, sessionId, areaId, venueId, row, col, available, checkedInTimestamp, status, orderId, userId, " +
            "price, priceName, metadata FROM TKT.TicketingSeatStatus " +
            "WHERE eventId = #{eventId} AND sessionId = #{sessionId} AND seatId = #{seatId}")
    @Results({
            @Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class),
            @Result(property = "status", column = "status", typeHandler = TicketingSeatStatusHandler.class)
    })
    TicketingSeat loadSeatInSession(@Param("eventId") UUID eventId,
                                  @Param("sessionId") UUID sessionId,
                                  @Param("seatId") UUID seatId);

    @Select("SELECT Areas.id, Areas.venueId, Areas.name, Areas.metadata FROM Areas " +
            "INNER JOIN Venues ON Areas.venueId = Venues.id " +
            "INNER JOIN Events ON Events.venueId = Venues.id " +
            "AND Events.id = #{eventId} AND Areas.id = #{areaId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Area loadAreaInEvent(@Param("eventId") UUID eventId, @Param("areaId") UUID areaId);

    @Select("SELECT Prices.id, name, price, eventId FROM PricesDistribution " +
            "INNER JOIN Prices ON Prices.id = PricesDistribution.priceId " +
            "AND eventId = #{eventId} " +
            "AND areaId = #{areaId}")
    Price loadAreaLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("areaId") UUID areaId);

    @Select("SELECT Prices.id, Prices.name, price, Events.id AS eventId FROM PricesDistribution " +
            "INNER JOIN Prices ON Prices.id = PricesDistribution.priceId " +
            "INNER JOIN Events ON Events.venueId = PricesDistribution.venueId " +
            "AND Events.id = Prices.eventId " +
            "AND Events.id = #{eventId}")
    Price loadDefaultPricingOfEvent(@Param("eventId") UUID eventId);

    @Select("SELECT id, eventId, name, price FROM Prices WHERE id = #{priceId} AND eventId = #{eventId}")
    Price loadPriceOfEventById(@Param("eventId") UUID eventId, @Param("priceId") UUID priceId);

    @Select("SELECT id, venueId, name, metadata FROM Events WHERE id = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Event loadEvent(@Param("eventId") UUID eventId);

    @Select("SELECT id, venueId, name, metadata FROM Events WHERE venueId = #{venueId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Event> loadEventsByVenue(@Param("venueId") String venueId);

    @Select("SELECT id, venueId, name, metadata FROM Events")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Event> loadAllEvents();

    @Select("SELECT venueId AS id, Venues.name, Venues.metadata " +
            "FROM Events " +
            "INNER JOIN Venues ON venueId = Venues.id " +
            "AND Events.id = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Venue loadVenueByEvent(@Param("eventId") UUID eventId);

    @Select("SELECT svg FROM Events " +
            "INNER JOIN Venues ON venueId = Venues.id " +
            "AND Events.id = #{eventId}")
    File loadEventVenueSvg(@Param("eventId") UUID eventId);

    @Insert("INSERT INTO Prices (id, eventId, name, price) VALUES (#{price.id}, #{eventId}, #{price.name}, #{price.price})")
    int _saveTicketPriceOfEvent(@Param("eventId") UUID eventId, @Param("price") Price price);

    default void saveTicketPriceOfEvent(UUID eventId, Price price) throws PersistenceException {
        if (_saveTicketPriceOfEvent(eventId, price) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully added", "304")
            );
    }

    @Delete("DELETE FROM PricesDistribution WHERE priceId IN (SELECT id FROM Prices WHERE eventId = #{eventId})")
    void _deletePriceDistributionByEvent(@Param("eventId") UUID eventId);

    @Delete("DELETE FROM Prices WHERE eventId = #{eventId}")
    void _deletePriceByEvent(@Param("eventId") UUID eventId);

    @Delete("DELETE FROM Events WHERE id = #{eventId}")
    void _deleteEvent(@Param("eventId") UUID eventId);

    @Transactional
    default void deleteEvent(UUID eventId) {
        _deletePriceDistributionByEvent(eventId);
        _deletePriceByEvent(eventId);
        _deleteEvent(eventId);
    }

    @Delete("DELETE FROM Prices " +
            "WHERE id = #{priceId} AND eventId = #{eventId} " +
            "AND NOT EXISTS (SELECT 1 FROM PricesDistribution WHERE priceId = #{priceId})")
    int _deleteTicketPriceOfEvent(@Param("eventId") UUID eventId, @Param("priceId") UUID priceId);

    default void deleteTicketPriceOfEvent(UUID eventId, UUID priceId) throws PersistenceException {
        if (_deleteTicketPriceOfEvent(eventId, priceId) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully deleted", "304")
            );
    }

    @Update("UPDATE PricesDistribution " +
            "SET priceId = #{priceId} " +
            "WHERE priceId IN (SELECT id FROM Prices WHERE eventId = #{eventId}) " +
            "AND seatId IS NULL " +
            "AND areaId IS NULL " +
            "AND venueId IN (SELECT venueId FROM Events WHERE id = #{eventId}) " +
            "AND NOT EXISTS (SELECT 1 FROM OrderSeats WHERE eventId = #{eventId}) ")
    int _updateDefaultPricingOfEvent(@Param("eventId") UUID eventId, @Param("priceId") UUID priceId);

    @Insert("INSERT INTO PricesDistribution (id, priceId, seatId, areaId, venueId) " +
            "SELECT #{newId}, #{priceId}, CAST(NULL AS VARCHAR(36)), CAST(NULL AS VARCHAR(36)), Events.venueId FROM Prices " +
            "INNER JOIN Events ON Events.id = Prices.eventId " +
            "AND Events.id = #{eventId} " +
            "WHERE Prices.id = #{priceId} " +
            "AND NOT EXISTS (SELECT 1 FROM OrderSeats WHERE eventId = #{eventId})")
    int _saveDefaultPricingOfEvent(@Param("newId") UUID newId, @Param("eventId") UUID eventId, @Param("priceId") UUID priceId);

    @Transactional
    default void saveDefaultPricingOfEvent(UUID eventId, UUID priceId) throws PersistenceException {
        if (_updateDefaultPricingOfEvent(eventId, priceId) < 1)
            if (_saveDefaultPricingOfEvent(UUID.randomUUID(), eventId, priceId) < 1)
                throw new PersistenceException(
                        new SQLException("Not successfully saved", "304")
                );
    }

    @Update("UPDATE PricesDistribution " +
            "SET priceId = #{priceId} " +
            "WHERE priceId IN (SELECT Prices.id FROM Prices WHERE eventId = #{eventId}) " +
            "AND seatId = #{seatId} " +
            "AND areaId IS NULL " +
            "AND venueId IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM OrderSeats WHERE eventId = #{eventId} AND seatId = #{seatId}) ")
    int _updateSeatLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId, @Param("priceId") UUID priceId);

    @Insert("INSERT INTO PricesDistribution (id, priceId, seatId, areaId, venueId) " +
            "SELECT #{newId}, #{priceId}, #{seatId}, CAST(NULL AS VARCHAR(36)), CAST(NULL AS VARCHAR(36)) FROM Prices " +
            "WHERE id = #{priceId} AND eventId = #{eventId} " +
            "AND NOT EXISTS (SELECT 1 FROM OrderSeats WHERE eventId = #{eventId} AND seatId = #{seatId})")
    int _saveSeatLevelPricingOfEvent(@Param("newId") UUID newId, @Param("eventId") UUID eventId, @Param("seatId") UUID seatId, @Param("priceId") UUID priceId);

    @Transactional
    default void saveSeatLevelPricingOfEvent(UUID eventId, UUID seatId, UUID priceId) throws PersistenceException {
        if (_updateSeatLevelPricingOfEvent(eventId, seatId, priceId) < 1)
            if (_saveSeatLevelPricingOfEvent(UUID.randomUUID(), eventId, seatId, priceId) < 1)
                throw new PersistenceException(
                        new SQLException("Not successfully saved", "304")
                );
    }

    @Delete("DELETE FROM PricesDistribution " +
            "WHERE priceId IN (SELECT Prices.id FROM Prices WHERE eventId = #{eventId}) " +
            "AND seatId = #{seatId} " +
            "AND areaId IS NULL " +
            "AND venueId IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM OrderSeats WHERE eventId = #{eventId} AND seatId = #{seatId}) ")
    int _deleteSeatLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("seatId") UUID seatId);

    default void deleteSeatLevelPricingOfEvent(UUID eventId, UUID seatId) throws PersistenceException {
        if (_deleteSeatLevelPricingOfEvent(eventId, seatId) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully deleted", "304")
            );
    }

    @Update("UPDATE PricesDistribution " +
            "SET priceId = #{priceId} " +
            "WHERE priceId IN (SELECT Prices.id FROM Prices WHERE eventId = #{eventId}) " +
            "AND seatId IS NULL " +
            "AND areaId = #{areaId} " +
            "AND venueId IS NULL " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM OrderSeats INNER JOIN Seats ON Seats.id = OrderSeats.seatId " +
            "WHERE OrderSeats.eventId = #{eventId} AND Seats.areaId = #{areaId}) ")
    int _updateAreaLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("areaId") UUID areaId, @Param("priceId") UUID priceId);

    @Insert("INSERT INTO PricesDistribution (id, priceId, seatId, areaId, venueId) " +
            "SELECT #{newId}, #{priceId}, CAST(NULL AS VARCHAR(36)), #{areaId}, CAST(NULL AS VARCHAR(36)) FROM Prices " +
            "WHERE id = #{priceId} AND eventId = #{eventId} " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM OrderSeats INNER JOIN Seats ON Seats.id = OrderSeats.seatId " +
            "WHERE OrderSeats.eventId = #{eventId} AND Seats.areaId = #{areaId})")
    int _saveAreaLevelPricingOfEvent(@Param("newId") UUID newId, @Param("eventId") UUID eventId, @Param("areaId") UUID areaId, @Param("priceId") UUID priceId);

    @Transactional
    default void saveAreaLevelPricingOfEvent(UUID eventId, UUID areaId, UUID priceId) throws PersistenceException{
        if (_updateAreaLevelPricingOfEvent(eventId, areaId, priceId) < 1)
            if (_saveAreaLevelPricingOfEvent(UUID.randomUUID(), eventId, areaId, priceId) < 1)
                throw new PersistenceException(
                        new SQLException("Not successfully saved", "304")
                );
    }

    @Delete("DELETE FROM PricesDistribution " +
            "WHERE priceId IN (SELECT Prices.id FROM Prices WHERE eventId = #{eventId}) " +
            "AND seatId IS NULL " +
            "AND areaId = #{areaId} " +
            "AND venueId IS NULL " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM OrderSeats INNER JOIN Seats ON Seats.id = OrderSeats.seatId " +
            "WHERE OrderSeats.eventId = #{eventId} AND Seats.areaId = #{areaId}) ")
    int _deleteAreaLevelPricingOfEvent(@Param("eventId") UUID eventId, @Param("areaId") UUID areaId);

    default void deleteAreaLevelPricingOfEvent(UUID eventId, UUID areaId) throws PersistenceException {
        if (_deleteAreaLevelPricingOfEvent(eventId, areaId) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully deleted", "304")
            );
    }

    @Insert("INSERT INTO Events (id, name, venueId, metadata) " +
            "VALUES (#{event.id}, #{event.name}, #{event.venueId}, " +
            "#{event.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler})")
    void saveEvent(@Param("event") Event event);

    @Update("UPDATE Events SET venueId = #{venueId} WHERE id = #{eventId}")
    int _updateVenueOfEvent(@Param("eventId") UUID eventId, @Param("venueId") UUID venueId);

    default void updateVenueOfEvent(UUID eventId, UUID venueId) throws PersistenceException {
        if (_updateVenueOfEvent(eventId, venueId) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully updated", "304")
            );
    }

    @Update("UPDATE Events SET " +
            "venueId = #{event.venueId}, " +
            "name = #{event.name}, " +
            "metadata = #{event.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler} " +
            "WHERE id = #{eventId}")
    int _updateEvent(@Param("eventId") UUID eventId, @Param("event") Event event);

    default void updateEvent(UUID eventId, Event event) throws PersistenceException {
        if (_updateEvent(eventId, event) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully updated", "304")
            );
    }

    @Select("SELECT contentType, content FROM EventPosters WHERE eventId = #{eventId}")
    EventPoster loadEventPoster(@Param("eventId") UUID eventId);

    @Delete("DELETE FROM EventPosters WHERE eventId = #{eventId}")
    void _deleteEventPoster(@Param("eventId") UUID eventId);

    @Insert("INSERT INTO EventPosters (id, eventId, contentType, content) " +
            "VALUES (#{posterId}, #{eventId}, #{poster.contentType}, #{poster.content})")
    int _insertEventPoster(@Param("posterId") UUID posterId,
                           @Param("eventId") UUID eventId,
                           @Param("poster") EventPoster poster);

    @Transactional
    default void saveEventPoster(UUID eventId, EventPoster poster) throws PersistenceException {
        _deleteEventPoster(eventId);
        if (_insertEventPoster(UUID.randomUUID(), eventId, poster) < 1)
            throw new PersistenceException(
                    new SQLException("Not successfully saved", "304")
            );
    }

    default void deleteEventPoster(UUID eventId) {
        _deleteEventPoster(eventId);
    }

    @Select("SELECT priceId, seatId, areaId, venueId FROM PricesDistribution " +
            "INNER JOIN Prices ON Prices.id = PricesDistribution.priceId " +
            "AND Prices.eventId = #{eventId}")
    List<Map<String, Object>> _loadAllPricingOfEvent(@Param("eventId") UUID eventId);

    @Insert("<script>INSERT INTO PricesDistribution VALUES " +
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

    @Insert("INSERT INTO SESSIONS (id, name, eventId, startTime, endTime, metadata) " +
            "VALUES (#{session.id}, #{session.name}, #{eventId}, #{session.startTime}, #{session.endTime}, " +
            "#{session.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler})")
    void saveSession(@Param("eventId") UUID eventId, @Param("session") Session session);

    @Update("UPDATE Sessions SET " +
            "name = #{session.name}, " +
            "startTime = #{session.startTime}, " +
            "endTime = #{session.endTime}, " +
            "metadata = #{session.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler} " +
            "WHERE id = #{sessionId} AND eventId = #{eventId}")
    void updateSession(@Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId, @Param("session") Session session);

    @Delete("DELETE FROM Sessions WHERE id = #{sessionId} AND eventId = #{eventId}")
    void deleteSession(@Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId);

    @Select("SELECT id, eventId, name, startTime, endTime, metadata FROM Sessions WHERE eventId = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Session> loadSessions(@Param("eventId") UUID eventId);

    @Select("SELECT id, eventId, name, startTime, endTime, metadata FROM Sessions WHERE id = #{sessionId} AND eventId = #{eventId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Session loadSession(@Param("eventId") UUID eventId, @Param("sessionId") UUID sessionId);
}
