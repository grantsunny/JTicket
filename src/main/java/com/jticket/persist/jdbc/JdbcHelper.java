package com.jticket.persist.jdbc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jticket.api.model.Area;
import com.jticket.api.model.Event;
import com.jticket.api.model.EventStatistics;
import com.jticket.api.model.Order;
import com.jticket.api.model.Price;
import com.jticket.api.model.Seat;
import com.jticket.api.model.Session;
import com.jticket.api.model.TicketingSeat;
import com.jticket.api.model.SessionStatistics;
import com.jticket.api.model.Venue;
import com.jticket.persist.OrdersRepository.PaymentResult;

import jakarta.inject.Inject;

@Component
@Profile("jdbc")
public class JdbcHelper {

    @Inject
    private DataSource dataSource;

    private String metadataMapToJsonString(Map<String, Object> metadataMap) {
        String metadataJson;
        try {
            metadataJson = new ObjectMapper().writeValueAsString(metadataMap);
        } catch (JsonProcessingException e) {
            metadataJson = "{}";
        }
        return metadataJson;
    }

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseMetadata(String rawMetadata) {
        if (rawMetadata == null || rawMetadata.isBlank()) {
            return new HashMap<>();
        }
        Map<String, Object> metadata;
        try {
            metadata = new ObjectMapper().readValue(
                    rawMetadata,
                    Map.class);
        } catch (JsonProcessingException ex) {
            metadata = new HashMap<>();
        }
        return metadata;
    }

    public List<Venue> loadAllVenues() throws SQLException {

        String sql = "SELECT id, name, metadata FROM TKT.Venues";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()
        ) {
            List<Venue> venues = new LinkedList<>();
            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                venues.add(new Venue().id(UUID.fromString(id))
                        .name(name)
                        .metadata(metadata));
            }
            return venues;
        }
    }

    public Venue loadVenueByEvent(UUID eventId) throws SQLException {

        String sql = "SELECT venueId, TKT.Venues.name, TKT.Venues.metadata " +
                "FROM TKT.Events " +
                "INNER JOIN TKT.Venues ON venueId = TKT.Venues.id " +
                "AND TKT.Events.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String venueId = rs.getString("venueId");
                    String name = rs.getString("name");
                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                    return new Venue().id(UUID.fromString(venueId))
                            .name(name)
                            .metadata(metadata);
                }
            }
        }
        return null;
    }

    public Venue loadVenue(UUID venueId) throws SQLException {
        String name;
        Map<String, Object> metadata;

        String sql = "SELECT id, name, metadata FROM TKT.Venues WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql))
        {
            pstmt.setString(1, venueId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    name = rs.getString("name");
                    metadata = parseMetadata(rs.getString("metadata"));
                    return new Venue().id(venueId)
                            .name(name)
                            .metadata(metadata);
                } else
                    return null;
            }
        }
    }

    public File loadEventVenueSvg(UUID eventId) throws SQLException {
        String sql = "SELECT svg FROM TKT.Events " +
                "INNER JOIN TKT.Venues ON venueId = TKT.Venues.id " +
                "AND TKT.Events.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    try {
                        File file = File.createTempFile(eventId.toString(), ".svg");
                        FileOutputStream fileOut = new FileOutputStream(file, false);

                        OutputStreamWriter writer = new OutputStreamWriter(fileOut, StandardCharsets.UTF_8);
                        writer.write(rs.getString("svg"));
                        writer.flush();
                        writer.close();

                        fileOut.close();
                        return file;
                    } catch (IOException e) {
                        return null;
                    }
                } else
                    return null;
            }
        }
    }

    public File loadVenueSvg(UUID venueId) throws SQLException {
        String sql = "SELECT svg FROM TKT.Venues WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, venueId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    try {
                        File file = File.createTempFile(venueId.toString(), ".svg");
                        FileOutputStream fileOut = new FileOutputStream(file, false);

                        OutputStreamWriter writer = new OutputStreamWriter(fileOut, StandardCharsets.UTF_8);
                        writer.write(rs.getString("svg"));
                        writer.flush();
                        writer.close();

                        fileOut.close();
                        return file;
                    } catch (IOException e) {
                        return null;
                    }
                } else
                    return null;
            }
        }
    }

    public void saveVenue(Venue venue, String svg) throws SQLException {
        String sql = "INSERT INTO TKT.Venues (id, name, metadata, svg) VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, venue.getId().toString());
            pstmt.setString(2, venue.getName());
            String metadataJson;
            try {
                metadataJson = new ObjectMapper().writeValueAsString(venue.getMetadata());
            } catch (JsonProcessingException e) {
                metadataJson = "{}";
            }
            pstmt.setString(3, metadataJson);
            pstmt.setString(4, svg);
            pstmt.executeUpdate();
        }
    }

    public List<Area> loadAreas(String venueId) throws SQLException {
        String sql = "SELECT id, name, metadata FROM TKT.Areas WHERE venueId = ?";
        List<Area> areas = new LinkedList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, venueId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");

                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                    areas.add(new Area().id(UUID.fromString(id))
                            .name(name)
                            .venueId(UUID.fromString(venueId))
                            .metadata(metadata));
                }
            }
        }
        return areas;
    }

    public Area loadArea(String areaId) throws IOException, SQLException {
        String sql = "SELECT venueId, name, metadata FROM TKT.Areas WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, areaId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String venueId = rs.getString("venueId");
                    String name = rs.getString("name");
                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                    return new Area().id(UUID.fromString(areaId))
                            .name(name)
                            .venueId(UUID.fromString(venueId))
                            .metadata(metadata);
                }
            }
        }
        return null;
    }


    public void saveAreas(List<Area> areas) throws SQLException {
        String sql = "INSERT INTO TKT.Areas (id, venueId, name, metadata) VALUES (?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Area area: areas) {
                pstmt.setString(1, area.getId().toString());
                pstmt.setString(2, area.getVenueId().toString());
                pstmt.setString(3, area.getName());

                String metadataJson;
                try {
                    metadataJson = new ObjectMapper().writeValueAsString(area.getMetadata());
                } catch (JsonProcessingException e) {
                    metadataJson = "{}";
                }
                pstmt.setString(4, metadataJson);
                pstmt.executeUpdate();
            }
        }
    }

    public void saveSeats(List<Seat> seats) throws SQLException {
        String sql = "INSERT INTO TKT.Seats (id, areaId, row, col, available, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (Seat seat: seats) {
                pstmt.setString(1, seat.getId().toString());
                pstmt.setString(2, seat.getAreaId().toString());
                pstmt.setInt(3, seat.getRow());
                pstmt.setInt(4, seat.getCol());
                pstmt.setBoolean(5, seat.getAvailable());

                String metadataJson;
                try {
                    metadataJson = new ObjectMapper().writeValueAsString(seat.getMetadata());
                } catch (JsonProcessingException e) {
                    metadataJson = "{}";
                }
                pstmt.setString(6, metadataJson);
                pstmt.executeUpdate();
            }
        }
    }

    public List<Seat> loadSeatsByVenue(UUID venueId) throws SQLException {
        String sql = "SELECT id, areaId, venueId, row, col, available, metadata " +
                "FROM TKT.SeatDetails WHERE venueId = ?";

        List<Seat> seats = new LinkedList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, venueId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    int row = rs.getInt("row");
                    int col = rs.getInt("col");
                    boolean available = rs.getBoolean("available");
                    String areaId = rs.getString("areaId");

                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                    seats.add(new Seat().id(UUID.fromString(id))
                            .venueId(venueId)
                            .areaId(UUID.fromString(areaId))
                            .row(row)
                            .col(col)
                            .available(available)
                            .metadata(metadata));
                }
            }
        }
        return seats;

    }

    public List<Seat> loadSeats(UUID venueId, UUID areaId) throws SQLException {

        if (areaId == null)
            return loadSeatsByVenue(venueId);

        String sql = "SELECT id, areaId, venueId, row, col, available, metadata " +
                "FROM TKT.SeatDetails WHERE venueId = ? AND areaId = ?";

        List<Seat> seats = new LinkedList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, venueId.toString());
            pstmt.setString(2, areaId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    int row = rs.getInt("row");
                    int col = rs.getInt("col");
                    boolean available = rs.getBoolean("available");

                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                    seats.add(new Seat().id(UUID.fromString(id))
                                    .venueId(venueId)
                                    .areaId(areaId)
                                    .row(row)
                                    .col(col)
                                    .available(available)
                                    .metadata(metadata));
                }
            }
        }
        return seats;
    }

    
    public Seat loadSeatInVenue(UUID venueId, UUID seatId) throws SQLException {
    	
        String sql = "SELECT areaId, TKT.Areas.venueId, row, col, available, TKT.Seats.metadata FROM TKT.Seats " +
                "INNER JOIN TKT.Areas ON TKT.Areas.id = TKT.Seats.areaId " +
                "AND TKT.Seats.id = ? AND TKT.Areas.venueId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, venueId.toString());
            pstmt.setString(2, seatId.toString());
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String areaId = rs.getString("areaId");
                    int row = rs.getInt("row");
                    int col = rs.getInt("col");
                    boolean available = rs.getBoolean("available");

                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                    return new Seat().id(seatId)
                            .areaId(UUID.fromString(areaId))
                            .venueId(venueId)
                            .row(row)
                            .col(col)
                            .available(available)
                            .metadata(metadata);
                }
            }
        }
        return null;
    }
    
    public Seat loadSeat(UUID seatId) throws SQLException {
        String sql = "SELECT areaId, TKT.Areas.venueId, row, col, available, TKT.Seats.metadata FROM TKT.Seats " +
                "INNER JOIN TKT.Areas ON TKT.Areas.id = TKT.Seats.areaId " +
                "AND TKT.Seats.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, seatId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String areaId = rs.getString("areaId");
                    String venueId = rs.getString("venueId");
                    int row = rs.getInt("row");
                    int col = rs.getInt("col");
                    boolean available = rs.getBoolean("available");

                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                    return new Seat().id(seatId)
                            .areaId(UUID.fromString(areaId))
                            .venueId(UUID.fromString(venueId))
                            .row(row)
                            .col(col)
                            .available(available)
                            .metadata(metadata);
                }
            }
        }
        return null;
    }

    @Transactional(rollbackFor = SQLException.class)
    public void saveEventAndCopyPrices(Event event, String copyFromEventId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            saveEvent(event, conn);

            /* Key: priceCopyFrom, Value: new priceId */
            Map<String, String> priceIdMap = new HashMap<>();

            String sqlCopyPrices = "SELECT id, name, price FROM TKT.Prices WHERE eventId = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlCopyPrices)) {
                stmt.setString(1, copyFromEventId);
                ResultSet rsCopyPrices = stmt.executeQuery();
                try (PreparedStatement stmtInsertCopiedPrices = conn.prepareStatement("INSERT INTO TKT.Prices VALUES (?, ?, ?, ?)")) {
                    while (rsCopyPrices.next()) {
                        String priceId = UUID.randomUUID().toString();
                        priceIdMap.put(rsCopyPrices.getString("id"), priceId);

                        stmtInsertCopiedPrices.setString(1, priceId);
                        stmtInsertCopiedPrices.setString(2, event.getId().toString());
                        stmtInsertCopiedPrices.setString(3, rsCopyPrices.getString("name"));
                        stmtInsertCopiedPrices.setBigDecimal(4, rsCopyPrices.getBigDecimal("price"));
                        stmtInsertCopiedPrices.addBatch();
                    }
                    stmtInsertCopiedPrices.executeBatch();
                }
            }

            String sqlCopyPriceDistribution = "SELECT priceId, seatId, areaId, venueId FROM TKT.PricesDistribution " +
                    "INNER JOIN TKT.Prices ON TKT.Prices.id = TKT.PricesDistribution.priceId " +
                    "AND TKT.Prices.eventId = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlCopyPriceDistribution)) {
                stmt.setString(1, copyFromEventId);
                ResultSet rsCopyPriceDistribution = stmt.executeQuery();
                try (PreparedStatement stmtInsertCopiedPriceDistribution = conn.prepareStatement(
                        "INSERT INTO TKT.PricesDistribution VALUES (?, ?, ?, ?, ?)")) {
                    while (rsCopyPriceDistribution.next()) {
                        String priceId = priceIdMap.get(rsCopyPriceDistribution.getString("priceId"));
                        if (priceId != null) {
                            stmtInsertCopiedPriceDistribution.setString(1, UUID.randomUUID().toString());
                            stmtInsertCopiedPriceDistribution.setString(2, priceId);
                            stmtInsertCopiedPriceDistribution.setString(3, rsCopyPriceDistribution.getString("seatId"));
                            stmtInsertCopiedPriceDistribution.setString(4, rsCopyPriceDistribution.getString("areaId"));
                            stmtInsertCopiedPriceDistribution.setString(5, rsCopyPriceDistribution.getString("venueId"));
                            stmtInsertCopiedPriceDistribution.addBatch();
                        }
                    }
                    stmtInsertCopiedPriceDistribution.executeBatch();
                }
            }
        }
    }

    public void saveEvent(Event event) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            saveEvent(event, conn);
        }
    }

    private void saveEvent(Event event, Connection connection) throws SQLException {
        String sql = "INSERT INTO TKT.Events (id, name, venueId, metadata) " +
                "VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, event.getId().toString());
            stmt.setString(2, event.getName());

            UUID venueId;
            if ((venueId = event.getVenueId()) != null)
                stmt.setString(3, venueId.toString());
            else
                stmt.setNull(3, Types.VARCHAR);

            String metadataJson;
            try {
                metadataJson = new ObjectMapper().writeValueAsString(event.getMetadata());
            } catch (JsonProcessingException e) {
                metadataJson = "{}";
            }
            stmt.setString(6, metadataJson);
            stmt.executeUpdate();
        }
    }

    public Event loadEvent(UUID eventId) throws SQLException {
        String sql = "SELECT venueId, name, metadata FROM TKT.Events WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (eventId == null)
                throw new SQLException("Specified UUID eventId is unexpected null");

            pstmt.setString(1, eventId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String venueId = rs.getString("venueId");
                    String name = rs.getString("name");

                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                    return new Event()
                            .id(eventId)
                            .venueId(UUID.fromString(venueId))
                            .name(name)
                            .metadata(metadata);
                }
            }
        }
        return null;
    }

    private List<Event> loadEvents(PreparedStatement stmt) throws SQLException {
        List<Event> events = new LinkedList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                String venueId = rs.getString("venueId");
                String name = rs.getString("name");

                Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

                events.add(new Event()
                        .id(id == null ? null : UUID.fromString(id))
                        .venueId(UUID.fromString(venueId))
                        .name(name)
                        .metadata(metadata));
            }
        }
        return events;
    }

    public List<Event> loadEventsByVenue(String venueId) throws SQLException {
        String sql = "SELECT id, venueId, name, metadata FROM TKT.Events " +
                "WHERE venueId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, venueId);
            return loadEvents(stmt);
        }
    }

    public List<Event> loadAllEvents() throws SQLException {
        String sql = "SELECT id, venueId, name, metadata FROM TKT.Events";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            return loadEvents(stmt);
        }
    }

    public void updateVenueOfEvent(UUID eventId, UUID venueId) throws SQLException {

        String sql = "UPDATE TKT.Events SET venueId = ? WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, venueId.toString());
            pstmt.setString(2, eventId.toString());
            pstmt.executeUpdate();

            if (pstmt.getUpdateCount() < 1)
                throw new SQLException("Not successfully modified", "304");
        }
    }

    public void updateEvent(UUID eventId, Event event) throws SQLException {
        //id, venueId, name, startTime, endTime, metadata
        String sql = "UPDATE TKT.Events SET " +
                "venueId = ?, " +
                "name = ?, " +
                "metadata = ? " +
                "WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            UUID venueId;
            if ((venueId = event.getVenueId()) != null)
                pstmt.setString(1, venueId.toString());
            else
                pstmt.setNull(1, Types.VARCHAR);

            pstmt.setString(2, event.getName());

            String metadataJson;
            try {
                metadataJson = new ObjectMapper().writeValueAsString(event.getMetadata());
            } catch (JsonProcessingException e) {
                metadataJson = "{}";
            }
            pstmt.setString(5, metadataJson);
            pstmt.setString(6, eventId.toString());
            pstmt.executeUpdate();

            if (pstmt.getUpdateCount() < 1)
                throw new SQLException("Not successfully modified", "304");
        }
    }

    public List<Price> loadPrices(UUID eventId) throws SQLException {
        String sql = "SELECT id, name, price FROM TKT.Prices Where eventId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Price> prices = new LinkedList<>();
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    BigDecimal price = rs.getBigDecimal("price");

                    prices.add(new Price().id(UUID.fromString(id))
                            .name(name)
                            .eventId(eventId)
                            .price((price == null) ? null : price.intValue()));
                }
                return prices;
            }
        }
    }

    public Price loadDefaultPricingOfEvent(UUID eventId) throws SQLException {
        String sql =
            "SELECT TKT.Prices.id, TKT.Prices.name, price FROM TKT.PricesDistribution " +
                    "INNER JOIN TKT.Prices ON TKT.Prices.id = TKT.PricesDistribution.priceId " +
                    "INNER JOIN TKT.Events ON TKT.Events.venueId = TKT.PricesDistribution.venueId " +
                    "AND TKT.Events.id = TKT.Prices.eventId " +
                    "AND TKT.Events.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    BigDecimal price = rs.getBigDecimal("price");

                    return new Price().id(UUID.fromString(id))
                            .name(name)
                            .eventId(eventId)
                            .price((price == null) ? null : price.intValue());
                }
                return null;
            }
        }
    }

    public Price loadAreaLevelPricingOfEvent(UUID eventId, UUID areaId) throws SQLException {
        String sql =
                "SELECT TKT.Prices.id, name, price FROM TKT.PricesDistribution " +
                        "INNER JOIN TKT.Prices ON TKT.Prices.id = TKT.PricesDistribution.priceId " +
                        "AND eventId = ? " +
                        "AND areaId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            pstmt.setString(2, areaId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    BigDecimal price = rs.getBigDecimal("price");

                    return new Price().id(UUID.fromString(id))
                            .name(name)
                            .eventId(eventId)
                            .price((price == null) ? null : price.intValue());
                }
                return null;
            }
        }
    }

    public Price loadSeatLevelPricingOfEvent(UUID eventId, UUID seatId) throws SQLException {
        String sql =
                "SELECT TKT.Prices.id, name, price FROM TKT.PricesDistribution " +
                "INNER JOIN TKT.Prices ON TKT.Prices.id = TKT.PricesDistribution.priceId " +
                "AND eventId = ? " +
                "AND seatId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            pstmt.setString(2, seatId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    BigDecimal price = rs.getBigDecimal("price");

                    return new Price().id(UUID.fromString(id))
                            .name(name)
                            .eventId(eventId)
                            .price((price == null) ? null : price.intValue());
                }
                return null;
            }
        }
    }

    public Price loadPriceOfEventById(UUID eventId, UUID priceId) throws SQLException {
        String sql =
                "SELECT name, price FROM TKT.Prices WHERE id = ? AND eventId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, priceId.toString());
            pstmt.setString(2, eventId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = priceId.toString();
                    String name = rs.getString("name");
                    BigDecimal price = rs.getBigDecimal("price");

                    return new Price().id(UUID.fromString(id))
                            .eventId(eventId)
                            .name(name)
                            .price((price == null) ? null : price.intValue());
                }
                return null;
            }
        }
    }



    public void deleteTicketPriceOfEvent(UUID eventId, UUID priceId) throws SQLException {
        String sql = "DELETE FROM TKT.Prices WHERE id = ? AND eventId = ? " +
                "AND NOT EXISTS (SELECT 1 FROM TKT.PricesDistribution WHERE priceId = ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, priceId.toString());
            pstmt.setString(2, eventId.toString());
            pstmt.setString(3, priceId.toString());
            pstmt.executeUpdate();

            if (pstmt.getUpdateCount() < 1)
                throw new SQLException("Not successfully deleted", "304");
        }
    }

    public List<Area> loadAllAreasInEvent(UUID eventId) throws SQLException {
        String sql = "SELECT TKT.Areas.id, TKT.Areas.venueId, TKT.Areas.name, TKT.Areas.metadata FROM TKT.Areas " +
                "INNER JOIN TKT.Venues ON TKT.Areas.venueId = TKT.Venues.id " +
                "INNER JOIN TKT.Events ON TKT.Events.venueId = TKT.Venues.id " +
                "AND TKT.Events.id = ?";

        List<Area> areas = new LinkedList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    String venueId = rs.getString("venueId");

                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
                    areas.add(new Area().id(UUID.fromString(id))
                            .name(name)
                            .venueId(UUID.fromString(venueId))
                            .metadata(metadata));
                }
            }
        }
        return areas;
    }

    private TicketingSeat readSeatFromSKU(ResultSet rs) throws SQLException {

        String id = rs.getString("seatId");
        int row = rs.getInt("row");
        int col = rs.getInt("col");
        String areaId = rs.getString("areaId");
        String venueId = rs.getString("venueId");


        boolean available = rs.getBoolean("available");
        String priceName = rs.getString("priceName");
        BigDecimal price = rs.getBigDecimal("price");
        Integer priceInCents = (price == null) ? null : price.intValue();

        Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));

        TicketingSeat seat = new TicketingSeat().id(UUID.fromString(id))
                .areaId(UUID.fromString(areaId))
                .venueId(UUID.fromString(venueId))
                .row(row)
                .col(col)
                .available(available)
                .price(priceInCents)
                .priceName(priceName)
                .sold(rs.getBoolean("sold"))
                .metadata(metadata);
        return seat;
    }

    public TicketingSeat loadSeatInEvent(UUID eventId, UUID seatId) throws SQLException {
        String sql =
                "SELECT id AS seatId, areaId, venueId, row, col, available, metadata, price, priceName, sold FROM " +
                        "TKT.SeatsInEvent WHERE id = ? AND eventId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, seatId.toString());
            pstmt.setString(2, eventId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return readSeatFromSKU(rs);
                }
                return null;
            }
        }
    }

    public List<TicketingSeat> loadSeatsInAreaOfEvent(UUID eventId, UUID areaId) throws SQLException {

        String sql =
                "SELECT id AS seatId, areaId, venueId, row, col, available, metadata, price, priceName, sold FROM " +
                "TKT.SEATSINEVENT WHERE eventId = ? AND areaId = ?";

        List<TicketingSeat> seats = new LinkedList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, eventId.toString());
            stmt.setString(2, areaId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    seats.add(readSeatFromSKU(rs));
                }
            }
        }
        return seats;
    }

    public Area loadAreaInEvent(UUID eventId, UUID areaId) throws SQLException {
        String sql = "SELECT TKT.Areas.venueId, TKT.Areas.name, TKT.Areas.metadata FROM TKT.Areas " +
                "INNER JOIN TKT.Venues ON TKT.Areas.venueId = TKT.Venues.id " +
                "INNER JOIN TKT.Events ON TKT.Events.venueId = TKT.Venues.id " +
                "AND TKT.Events.id = ? AND TKT.Areas.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            pstmt.setString(2, areaId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    String venueId = rs.getString("venueId");

                    Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
                    return new Area().id(areaId)
                            .name(name)
                            .venueId(UUID.fromString(venueId))
                            .metadata(metadata);
                }
                return null;
            }
        }
    }

    public void saveTicketPriceOfEvent(UUID eventId, Price price) throws SQLException {
        String sql = "INSERT INTO TKT.Prices (id, eventId, name, price) VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, price.getId().toString());
            pstmt.setString(2, eventId.toString());
            pstmt.setString(3, price.getName());

            pstmt.setBigDecimal(4, BigDecimal.valueOf(price.getPrice()));

            pstmt.executeUpdate();
            if (pstmt.getUpdateCount() < 1)
                throw new SQLException("Not successfully added", "304");
        }
    }

    @Transactional(rollbackFor = SQLException.class)
    public void saveDefaultPricingOfEvent(UUID eventId, UUID priceId) throws SQLException {

        String sql =
                "UPDATE TKT.PricesDistribution " +
                "SET priceId = ? " +
                "WHERE priceId IN (SELECT id FROM TKT.Prices WHERE eventId = ?) " +
                "AND seatId IS NULL " +
                "AND areaId IS NULL " +
                "AND venueId IN (SELECT venueId FROM TKT.Events WHERE id = ?) " +
                "AND NOT EXISTS (SELECT 1 FROM TKT.OrderSeats WHERE eventId = ?) ";

        String sql2 =
                "INSERT INTO TKT.PricesDistribution (id, priceId, seatId, areaId, venueId) " +
                "SELECT ?, ?, CAST(NULL AS VARCHAR(36)), CAST(NULL AS VARCHAR(36)), TKT.Events.venueId FROM TKT.Prices " +
                "INNER JOIN TKT.Events ON TKT.Events.id = TKT.Prices.eventId " +
                "AND TKT.Events.id = ? " +
                "WHERE TKT.Prices.id = ? " +
                "AND NOT EXISTS (SELECT 1 FROM TKT.OrderSeats WHERE eventId = ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, priceId.toString());
            pstmt.setString(2, eventId.toString());
            pstmt.setString(3, eventId.toString());
            pstmt.setString(4, eventId.toString());

            if (pstmt.executeUpdate() < 1) {
                try (PreparedStatement pstmt2 = conn.prepareStatement(sql2)) {
                    pstmt2.setString(1, UUID.randomUUID().toString());
                    pstmt2.setString(2, priceId.toString());
                    pstmt2.setString(3, eventId.toString());
                    pstmt2.setString(4, priceId.toString());
                    pstmt2.setString(5, eventId.toString());

                    pstmt2.executeUpdate();
                }
            }
        }
    }

    @Transactional(rollbackFor = SQLException.class)
    public void saveSeatLevelPricingOfEvent(UUID eventId, UUID seatId, UUID priceId) throws SQLException {
        String sql =
                "UPDATE TKT.PricesDistribution " +
                        "SET priceId = ? " +
                        "WHERE priceId IN (SELECT TKT.Prices.id FROM TKT.Prices WHERE eventId = ?) " +
                        "AND seatId = ? " +
                        "AND areaId IS NULL " +
                        "AND venueId IS NULL " +
                        "AND NOT EXISTS (SELECT 1 FROM TKT.OrderSeats WHERE eventId = ? AND seatId = ?) ";

        String sql2 =
                "INSERT INTO TKT.PricesDistribution (id, priceId, seatId, areaId, venueId) " +
                        "SELECT ?, ?, ?, CAST(NULL AS VARCHAR(36)), CAST(NULL AS VARCHAR(36)) FROM TKT.Prices " +
                        "WHERE id = ? AND eventId = ? " +
                        "AND NOT EXISTS (SELECT 1 FROM TKT.OrderSeats WHERE eventId = ? AND seatId = ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, priceId.toString());
            pstmt.setString(2, eventId.toString());
            pstmt.setString(3, seatId.toString());
            pstmt.setString(4, eventId.toString());
            pstmt.setString(5, seatId.toString());

            if (pstmt.executeUpdate() < 1) {
                try (PreparedStatement pstmt2 = conn.prepareStatement(sql2)) {
                    pstmt2.setString(1, UUID.randomUUID().toString());
                    pstmt2.setString(2, priceId.toString());
                    pstmt2.setString(3, seatId.toString());
                    pstmt2.setString(4, priceId.toString());
                    pstmt2.setString(5, eventId.toString());
                    pstmt2.setString(6, eventId.toString());
                    pstmt2.setString(7, seatId.toString());

                    pstmt2.executeUpdate();
                }
            }
        }
    }

    public void deleteSeatLevelPricingOfEvent(UUID eventId, UUID seatId) throws SQLException {
        String sql =
                "DELETE FROM TKT.PricesDistribution " +
                        "WHERE priceId IN (SELECT TKT.Prices.id FROM TKT.Prices WHERE eventId = ?) " +
                        "AND seatId = ? " +
                        "AND areaId IS NULL " +
                        "AND venueId IS NULL " +
                        "AND NOT EXISTS (SELECT 1 FROM TKT.OrderSeats WHERE eventId = ? AND seatId = ?) ";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            pstmt.setString(2, seatId.toString());
            pstmt.setString(3, eventId.toString());
            pstmt.setString(4, seatId.toString());

            if (pstmt.executeUpdate() < 1) {
                throw new SQLException("Not successfully deleted", "304");
            }
        }
    }

    @Transactional(rollbackFor = SQLException.class)
    public void saveAreaLevelPricingOfEvent(UUID eventId, UUID areaId, UUID priceId) throws SQLException {

        String sql =
                "UPDATE TKT.PricesDistribution " +
                        "SET priceId = ? " +
                        "WHERE priceId IN (SELECT TKT.Prices.id FROM TKT.Prices WHERE eventId = ?) " +
                        "AND seatId IS NULL " +
                        "AND areaId = ? " +
                        "AND venueId IS NULL " +
                        "AND NOT EXISTS (" +
                        "SELECT 1 FROM TKT.OrderSeats INNER JOIN TKT.Seats ON TKT.Seats.id = TKT.OrderSeats.seatId " +
                        "WHERE TKT.OrderSeats.eventId = ? AND TKT.Seats.areaId = ?) ";

        String sql2 =
                "INSERT INTO TKT.PricesDistribution (id, priceId, seatId, areaId, venueId) " +
                        "SELECT ?, ?, CAST(NULL AS VARCHAR(36)), ?, CAST(NULL AS VARCHAR(36)) FROM TKT.Prices " +
                        "WHERE id = ? AND eventId = ? " +
                        "AND NOT EXISTS (" +
                        "SELECT 1 FROM TKT.OrderSeats INNER JOIN TKT.Seats ON TKT.Seats.id = TKT.OrderSeats.seatId " +
                        "WHERE TKT.OrderSeats.eventId = ? AND TKT.Seats.areaId = ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, priceId.toString());
            pstmt.setString(2, eventId.toString());
            pstmt.setString(3, areaId.toString());
            pstmt.setString(4, eventId.toString());
            pstmt.setString(5, areaId.toString());

            if (pstmt.executeUpdate() < 1) {
                try (PreparedStatement pstmt2 = conn.prepareStatement(sql2)) {
                    pstmt2.setString(1, UUID.randomUUID().toString());
                    pstmt2.setString(2, priceId.toString());
                    pstmt2.setString(3, areaId.toString());
                    pstmt2.setString(4, priceId.toString());
                    pstmt2.setString(5, eventId.toString());
                    pstmt2.setString(6, eventId.toString());
                    pstmt2.setString(7, areaId.toString());

                    pstmt2.executeUpdate();
                }
            }
        }
    }

    public void deleteAreaLevelPricingOfEvent(UUID eventId, UUID areaId) throws SQLException {
        String sql =
                "DELETE FROM TKT.PricesDistribution " +
                        "WHERE priceId IN (SELECT TKT.Prices.id FROM TKT.Prices WHERE eventId = ?) " +
                        "AND seatId IS NULL " +
                        "AND areaId = ? " +
                        "AND venueId IS NULL " +
                        "AND NOT EXISTS (" +
                        "SELECT 1 FROM TKT.OrderSeats INNER JOIN TKT.Seats ON TKT.Seats.id = TKT.OrderSeats.seatId " +
                        "WHERE TKT.OrderSeats.eventId = ? AND TKT.Seats.areaId = ?) ";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            pstmt.setString(2, areaId.toString());
            pstmt.setString(3, eventId.toString());
            pstmt.setString(4, areaId.toString());

            if (pstmt.executeUpdate() < 1) {
                throw new SQLException("Not successfully deleted", "304");
            }
        }
    }

    @Transactional(rollbackFor = SQLException.class)
    public void deleteEvent(UUID eventId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String[] sqlCleanUp = {
                    "DELETE FROM TKT.PricesDistribution WHERE priceId IN (SELECT id FROM TKT.Prices WHERE eventId = ?)",
                    "DELETE FROM TKT.Prices WHERE eventId = ?",
                    "DELETE FROM TKT.Events WHERE id = ?"
            };

            for (String sql: sqlCleanUp) {
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, eventId.toString());
                    stmt.executeUpdate();
                }
            }
        }
    }


    public boolean isUserOrderExist(String userId, UUID orderId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM TKT.ORDERS WHERE USERID = ? AND ID = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            stmt.setString(2, orderId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
            return false;
        }
    }

    
	public int checkInSeat(UUID eventId, UUID sessionId, UUID seatId) throws SQLException {
    	
        try (Connection conn = dataSource.getConnection()) {
            String sqlCheckinSeat = "UPDATE OrderSeats SET checkedInTimestamp = CURRENT_TIMESTAMP " +
                    "WHERE eventId = ? AND sessionId = ? AND seatId = ? " +
                    "AND checkedInTimestamp IS NULL";
            
            try (PreparedStatement stmt = conn.prepareStatement(sqlCheckinSeat)) {
                stmt.setString(1, eventId.toString());
                stmt.setString(2, sessionId.toString());
                stmt.setString(3, seatId.toString());
                return stmt.executeUpdate();
            }
        }
    }

    public EventStatistics loadEventStatistics(UUID eventId) throws SQLException {
        String sql = "SELECT eventId, totalSeats, orderedSeats, checkedInSeats, uncheckedInSeats " +
                "FROM TKT.EventStatistics WHERE eventId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, eventId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new EventStatistics()
                            .eventId(UUID.fromString(rs.getString("eventId")))
                            .totalSeats(rs.getInt("totalSeats"))
                            .orderedSeats(rs.getInt("orderedSeats"))
                            .checkedInSeats(rs.getInt("checkedInSeats"))
                            .uncheckedInSeats(rs.getInt("uncheckedInSeats"));
                }
            }
        }
        return null;
    }

    public SessionStatistics loadSessionStatistics(UUID eventId, UUID sessionId) throws SQLException {
        String sql = "SELECT eventId, sessionId, totalSeats, orderedSeats, checkedInSeats, uncheckedInSeats " +
                "FROM TKT.SessionStatistics WHERE eventId = ? AND sessionId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, eventId.toString());
            stmt.setString(2, sessionId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new SessionStatistics()
                            .eventId(UUID.fromString(rs.getString("eventId")))
                            .sessionId(UUID.fromString(rs.getString("sessionId")))
                            .totalSeats(rs.getInt("totalSeats"))
                            .orderedSeats(rs.getInt("orderedSeats"))
                            .checkedInSeats(rs.getInt("checkedInSeats"))
                            .uncheckedInSeats(rs.getInt("uncheckedInSeats"));
                }
            }
        }
        return null;
    }

    public List<TicketingSeat> loadSeatsInAreaOfSession(UUID eventId, UUID sessionId, UUID areaId) throws SQLException {
        String sql = "SELECT seatId, sessionId, areaId, venueId, row, col, available, metadata, price, priceName, " +
                "orderId, userId, checkedInTimestamp, status FROM TKT.TicketingSeatStatus " +
                "WHERE eventId = ? AND sessionId = ? AND areaId = ? ORDER BY row, col";

        List<TicketingSeat> seats = new LinkedList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, eventId.toString());
            stmt.setString(2, sessionId.toString());
            stmt.setString(3, areaId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    seats.add(readSeatFromTicketingSeatStatus(rs));
                }
            }
        }
        return seats;
    }

    public TicketingSeat loadSeatInSession(UUID eventId, UUID sessionId, UUID seatId) throws SQLException {
        String sql = "SELECT seatId, sessionId, areaId, venueId, row, col, available, metadata, price, priceName, " +
                "orderId, userId, checkedInTimestamp, status FROM TKT.TicketingSeatStatus " +
                "WHERE eventId = ? AND sessionId = ? AND seatId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, eventId.toString());
            stmt.setString(2, sessionId.toString());
            stmt.setString(3, seatId.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return readSeatFromTicketingSeatStatus(rs);
                }
            }
        }
        return null;
    }

    private TicketingSeat readSeatFromTicketingSeatStatus(ResultSet rs) throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");
        String orderId = rs.getString("orderId");

        TicketingSeat seat = new TicketingSeat()
                .id(UUID.fromString(rs.getString("seatId")))
                .sessionId(UUID.fromString(rs.getString("sessionId")))
                .areaId(UUID.fromString(rs.getString("areaId")))
                .venueId(UUID.fromString(rs.getString("venueId")))
                .row(rs.getInt("row"))
                .col(rs.getInt("col"))
                .available(rs.getBoolean("available"))
                .checkedInTimestamp(rs.getTimestamp("checkedInTimestamp"))
                .status(TicketingSeat.StatusEnum.fromValue(rs.getString("status")))
                .userId(rs.getString("userId"))
                .price((price == null) ? null : price.intValue())
                .priceName(rs.getString("priceName"))
                .metadata(parseMetadata(rs.getString("metadata")));

        if (orderId != null)
            seat.orderId(UUID.fromString(orderId));
        return seat;
    }
    
    @Transactional(rollbackFor = SQLException.class)
    public void saveNewOrder(Order order) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            if (order.getId() == null)
                order.id(UUID.randomUUID());

            String sqlNewOrder = "INSERT INTO TKT.ORDERS(ID, EVENTID, SESSIONID, USERID, TIMESTAMP, METADATA) " +
                    "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sqlNewOrder)) {
                stmt.setString(1, order.getId().toString());
                stmt.setString(2, order.getEventId().toString());
                stmt.setString(3, order.getSessionId().toString());
                stmt.setString(4, order.getUserId());
                stmt.setString(5, metadataMapToJsonString(order.getMetadata()));
                stmt.executeUpdate();
            }

            String sqlNewOrderSeat = "INSERT INTO TKT.ORDERSEATS(ORDERID, EVENTID, SESSIONID, SEATID, METADATA) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sqlNewOrderSeat)) {
                for (var seatEntry: order.getSeats()) {
                    stmt.setString(1, order.getId().toString());
                    stmt.setString(2, order.getEventId().toString());
                    stmt.setString(3, order.getSessionId().toString());
                    stmt.setString(4, seatEntry.getId().toString());
                    stmt.setString(5, metadataMapToJsonString(seatEntry.getMetadata()));
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        }
    }

    private List<Order> loadOrders(Connection conn, ResultSet rsOrders) throws SQLException {
        List<Order> loadOrders = new LinkedList<>();
        while (rsOrders.next()) {
            Order order = new Order()
                    .id(UUID.fromString(rsOrders.getString("id")))
                    .eventId(UUID.fromString(rsOrders.getString("eventId")))
                    .sessionId(UUID.fromString(rsOrders.getString("sessionId")))
                    .userId(rsOrders.getString("userId"))
                    .timestamp(rsOrders.getTimestamp("timeStamp"))
                    .paymentChannel(rsOrders.getString("paymentChannel"))
                    .paymentTransactionId(rsOrders.getString("paymentTransactionId"))
                    .paymentTimestamp(rsOrders.getTimestamp("paymentTimestamp"))
                    .paymentAmount(rsOrders.getInt("paymentAmount"))
                    .metadata(parseMetadata(rsOrders.getString("metadata")));

            String sqlSeats = "SELECT seatId, sessionId, areaId, venueId, row, col, available, metadata, price, priceName, " +
                    "orderId, userId, checkedInTimestamp, status FROM TKT.TicketingSeatStatus WHERE orderId = ? ORDER BY row, col";
            
            try (PreparedStatement stmtSeat = conn.prepareStatement(sqlSeats)) {
                stmtSeat.setString(1, order.getId().toString());
                ResultSet rsSeats = stmtSeat.executeQuery();

                while (rsSeats.next()) {
                    order.getSeats().add(readSeatFromTicketingSeatStatus(rsSeats));
                }
            }
            loadOrders.add(order);
        }
        return loadOrders;
    }


    public List<Order> loadOrders(String userId, Date startTime, Date endTime) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, SESSIONID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM TKT.ORDERS " +
                    "WHERE USERID = ? AND TIMESTAMP BETWEEN ? AND ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                stmt.setString(1, userId);
                stmt.setTimestamp(2, new Timestamp(startTime.getTime()));
                stmt.setTimestamp(3, new Timestamp(endTime.getTime()));
                ResultSet rsOrders = stmt.executeQuery();

                return loadOrders(conn, rsOrders);
            }
        }
    }

    public List<Order> loadOrders(Date startTime, Date endTime) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, SESSIONID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM TKT.ORDERS " +
                    "WHERE TIMESTAMP BETWEEN ? AND ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                stmt.setTimestamp(1, new Timestamp(startTime.getTime()));
                stmt.setTimestamp(2, new Timestamp(endTime.getTime()));
                ResultSet rsOrders = stmt.executeQuery();

                return loadOrders(conn, rsOrders);
            }
        }

    }

    public List<Order> loadOrders(String userId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, SESSIONID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM TKT.ORDERS " +
                    "WHERE USERID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                stmt.setString(1, userId);
                ResultSet rsOrders = stmt.executeQuery();

                return loadOrders(conn, rsOrders);
            }
        }
    }

    public List<Order> loadOrders(UUID eventId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, SESSIONID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM TKT.ORDERS " +
                    "WHERE EVENTID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                stmt.setString(1, eventId.toString());
                ResultSet rsOrders = stmt.executeQuery();

                return loadOrders(conn, rsOrders);
            }
        }
    }

    public List<Order> loadOrders() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, SESSIONID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM TKT.ORDERS";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                ResultSet rsOrders = stmt.executeQuery();
                return loadOrders(conn, rsOrders);
            }
        }
    }

    public Order loadOrder(UUID orderId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, SESSIONID, USERID, TIMESTAMP, PAYMENTCHANNEL, PAYMENTTRANSACTIONID, PAYMENTTIMESTAMP, PAYMENTAMOUNT, METADATA FROM TKT.ORDERS " +
                    "WHERE ID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                stmt.setString(1, orderId.toString());
                ResultSet rsOrders = stmt.executeQuery();

                List<Order> orders = loadOrders(conn, rsOrders);
                return orders.isEmpty() ? null : orders.get(0);
            }
        }
    }

    @Transactional(rollbackFor = SQLException.class)
    public PaymentResult updateOrderPayment(UUID orderId, String paymentChannel, String paymentTransactionId, Integer paymentAmount) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE TKT.ORDERS SET PAYMENTCHANNEL = ?, PAYMENTTRANSACTIONID = ?, PAYMENTAMOUNT = ?, PAYMENTTIMESTAMP = CURRENT_TIMESTAMP " +
                    "WHERE ID = ? AND PAYMENTTRANSACTIONID IS NULL " +
                    "AND NOT EXISTS (SELECT 1 FROM TKT.ORDERS WHERE PAYMENTTRANSACTIONID = ? AND ID <> ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, paymentChannel);
                stmt.setString(2, paymentTransactionId);
                stmt.setInt(3, paymentAmount);
                stmt.setString(4, orderId.toString());
                stmt.setString(5, paymentTransactionId);
                stmt.setString(6, orderId.toString());
                if (stmt.executeUpdate() > 0)
                    return PaymentResult.SUCCESS;
            }

            String classifySql = "SELECT CASE " +
                    "WHEN COUNT(*) = 0 THEN 'ORDER_NOT_FOUND' " +
                    "WHEN MAX(PAYMENTTRANSACTIONID) = ? AND MAX(PAYMENTCHANNEL) = ? AND MAX(PAYMENTAMOUNT) = ? THEN 'IDEMPOTENT_RETRY' " +
                    "ELSE 'CONFLICT' END AS PAYMENTRESULT " +
                    "FROM TKT.ORDERS WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(classifySql)) {
                stmt.setString(1, paymentTransactionId);
                stmt.setString(2, paymentChannel);
                stmt.setInt(3, paymentAmount);
                stmt.setString(4, orderId.toString());
                ResultSet rs = stmt.executeQuery();
                rs.next();
                return PaymentResult.valueOf(rs.getString("PAYMENTRESULT"));
            }
        }
    }

    @Transactional
    public void updateOrderMetadata(Order order) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlUpdateOrder = "UPDATE TKT.ORDERS SET METADATA = ? WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpdateOrder)) {
                stmt.setString(1, metadataMapToJsonString(order.getMetadata()));
                stmt.setString(2, order.getId().toString());
                stmt.executeUpdate();
            }

            String sqlUpdateSeat = "UPDATE TKT.ORDERSEATS SET METADATA = ? " +
                    "WHERE ORDERID = ? AND EVENTID = ? AND SEATID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpdateSeat)) {
                for (var seatEntry: order.getSeats()) {
                    stmt.setString(1, metadataMapToJsonString(seatEntry.getMetadata()));
                    stmt.setString(2, order.getId().toString());
                    stmt.setString(3, order.getEventId().toString());
                    stmt.setString(4, seatEntry.getId().toString());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        }
    }

    @Transactional(rollbackFor = SQLException.class)
    public void deleteOrder(UUID orderId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlSeats = "DELETE FROM TKT.ORDERSEATS WHERE ORDERID = ?";
            String sqlOrder = "DELETE FROM TKT.ORDERS WHERE ID = ?";

            try (PreparedStatement stmtSeats = conn.prepareStatement(sqlSeats);
                 PreparedStatement stmtOrder = conn.prepareStatement(sqlOrder)) {

                stmtSeats.setString(1, orderId.toString());
                stmtSeats.executeUpdate();

                stmtOrder.setString(1, orderId.toString());
                stmtOrder.executeUpdate();
            }
        }
    }

    public void saveSession(UUID eventId, Session session) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO TKT.SESSIONS (id, name, eventId, startTime, endTime, metadata) VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, session.getId().toString());
                stmt.setString(2, session.getName());
                stmt.setString(3, eventId.toString());
                stmt.setTimestamp(4, new Timestamp(session.getStartTime().getTime()));
                stmt.setTimestamp(5, new Timestamp(session.getEndTime().getTime()));
                stmt.setString(6, metadataMapToJsonString(session.getMetadata()));
                stmt.executeUpdate();
            }
        }
    }

    public void updateSession(UUID eventId, UUID sessionId, Session session) throws SQLException {
        String sql = "UPDATE TKT.Sessions SET " +
                "name = ?, " +
                "startTime = ?, " +
                "endTime = ?, " +
                "metadata = ? " +
                "WHERE id = ? AND eventId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, session.getName());
            stmt.setTimestamp(2, new Timestamp(session.getStartTime().getTime()));
            stmt.setTimestamp(3, new Timestamp(session.getEndTime().getTime()));
            stmt.setString(4, metadataMapToJsonString(session.getMetadata()));

            stmt.setString(5, sessionId.toString());
            stmt.setString(6, eventId.toString());

            stmt.executeUpdate();
            if (stmt.getUpdateCount() < 1)
                throw new SQLException("Not successfully modified", "304");
        }
    }

    public void deleteSession(UUID eventId, UUID sessionId) throws SQLException {
        String sql = "DELETE FROM TKT.Sessions WHERE id = ? AND eventId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sessionId.toString());
            stmt.setString(2, eventId.toString());

            stmt.executeUpdate();
            if (stmt.getUpdateCount() < 1)
                throw new SQLException("Not successfully modified", "304");
        }
    }

    public List<Session> loadSessions(UUID eventId) throws SQLException {
        String sql = "SELECT id, eventId, name, startTime, endTime, metadata FROM TKT.Sessions WHERE eventId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            List<Session> sessions = new LinkedList<>();
            stmt.setString(1, eventId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(new Session().id(UUID.fromString(rs.getString("id")))
                            .eventId(UUID.fromString(rs.getString("eventId")))
                            .name(rs.getString("name"))
                            .startTime(rs.getTimestamp("startTime"))
                            .endTime(rs.getTimestamp("endTime"))
                            .metadata(parseMetadata(rs.getString("metadata")))
                    );
                }
            }
            return sessions;
        }
    }

    public Session loadSession(UUID eventId, UUID sessionId) throws SQLException {
        String sql = "SELECT id, eventId, name, startTime, endTime, metadata FROM TKT.Sessions WHERE id = ? AND eventId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sessionId.toString());
            stmt.setString(2, eventId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Session().id(UUID.fromString(rs.getString("id")))
                            .eventId(UUID.fromString(rs.getString("eventId")))
                            .name(rs.getString("name"))
                            .startTime(rs.getTimestamp("startTime"))
                            .endTime(rs.getTimestamp("endTime"))
                            .metadata(parseMetadata(rs.getString("metadata")));
                } else
                    return null;
            }
        }
    }
}
