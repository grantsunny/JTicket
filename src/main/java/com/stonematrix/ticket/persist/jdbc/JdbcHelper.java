package com.stonematrix.ticket.persist.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonematrix.ticket.api.model.*;
import jakarta.inject.Inject;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Date;
import java.util.*;

@Component
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

    private Map<String, Object> parseMetadata(String rawMetadata) {
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
                Map<String, Object> metadata;
                try {
                    metadata = new ObjectMapper().readValue(
                            rs.getString("metadata"),
                            Map.class);
                } catch (JsonProcessingException ex) {
                    metadata = new HashMap<>();
                }

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
                    Map<String, Object> metadata;
                    try {
                        metadata = new ObjectMapper().readValue(
                                rs.getString("metadata"),
                                Map.class);
                    } catch (JsonProcessingException ex) {
                        metadata = new HashMap<>();
                    }

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
                    try {
                        metadata = new ObjectMapper().readValue(
                                rs.getString("metadata"),
                                Map.class);
                    } catch (JsonProcessingException ex) {
                        metadata = new HashMap<>();
                    }
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

                    Map<String, Object> metadata;
                    try {
                        metadata = new ObjectMapper().readValue(
                                rs.getString("metadata"),
                                Map.class);
                    } catch (JsonProcessingException ex) {
                        metadata = new HashMap<>();
                    }

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
                    Map<String, Object> metadata;
                    try {
                        metadata = new ObjectMapper().readValue(
                                rs.getString("metadata"),
                                Map.class);
                    } catch (JsonProcessingException ex) {
                        metadata = new HashMap<>();
                    }

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
                pstmt.setInt(4, seat.getColumn());
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

                    Map<String, Object> metadata;
                    try {
                        metadata = new ObjectMapper().readValue(
                                rs.getString("metadata"),
                                Map.class);
                    } catch (JsonProcessingException ex) {
                        metadata = new HashMap<>();
                    }

                    seats.add(new Seat().id(UUID.fromString(id))
                            .venueId(venueId)
                            .areaId(UUID.fromString(areaId))
                            .row(row)
                            .column(col)
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

                    Map<String, Object> metadata;
                    try {
                        metadata = new ObjectMapper().readValue(
                                rs.getString("metadata"),
                                Map.class);
                    } catch (JsonProcessingException ex) {
                        metadata = new HashMap<>();
                    }

                    seats.add(new Seat().id(UUID.fromString(id))
                                    .venueId(venueId)
                                    .areaId(areaId)
                                    .row(row)
                                    .column(col)
                                    .available(available)
                                    .metadata(metadata));
                }
            }
        }
        return seats;
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

                    Map<String, Object> metadata;
                    try {
                        metadata = new ObjectMapper().readValue(
                                rs.getString("metadata"),
                                Map.class);
                    } catch (JsonProcessingException ex) {
                        metadata = new HashMap<>();
                    }

                    return new Seat().id(seatId)
                            .areaId(UUID.fromString(areaId))
                            .venueId(UUID.fromString(venueId))
                            .row(row)
                            .column(col)
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

                    Map<String, Object> metadata;
                    try {
                        metadata = new ObjectMapper().readValue(
                                rs.getString("metadata"),
                                Map.class);
                    } catch (JsonProcessingException ex) {
                        metadata = new HashMap<>();
                    }

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

                Map<String, Object> metadata;
                try {
                    metadata = new ObjectMapper().readValue(
                            rs.getString("metadata"),
                            Map.class);
                } catch (JsonProcessingException ex) {
                    metadata = new HashMap<>();
                }

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
                            .price((price == null) ? null : price.multiply(BigDecimal.valueOf(100L)).intValue()));
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
                            .price((price == null) ? null : price.multiply(BigDecimal.valueOf(100L)).intValue());
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
                            .price((price == null) ? null : price.multiply(BigDecimal.valueOf(100L)).intValue());
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
                            .price((price == null) ? null : price.multiply(BigDecimal.valueOf(100L)).intValue());
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
                            .price((price == null) ? null : price.multiply(BigDecimal.valueOf(100L)).intValue());
                }
                return null;
            }
        }
    }



    public void deleteTicketPriceOfEvent(UUID eventId, UUID priceId) throws SQLException {
        String sql = "DELETE FROM TKT.Prices WHERE id = ? AND eventId = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, priceId.toString());
            pstmt.setString(2, eventId.toString());
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

    private Seat readSeatFromSKU(ResultSet rs) throws SQLException {

        String id = rs.getString("seatId");
        int row = rs.getInt("row");
        int col = rs.getInt("col");
        String areaId = rs.getString("areaId");
        String venueId = rs.getString("venueId");


        boolean available = rs.getBoolean("available");
        String orderId = rs.getString("orderId");
        String priceName = rs.getString("priceName");
        BigDecimal price = rs.getBigDecimal("price");

        Map<String, Object> metadata = new HashMap<>(
                parseMetadata(rs.getString("metadata")));

        metadata.put("orderId", orderId);
        metadata.put("price", (price == null) ? null : price.multiply(BigDecimal.valueOf(100L)).intValue());
        metadata.put("priceName", priceName);

        return new Seat().id(UUID.fromString(id))
                .areaId(UUID.fromString(areaId))
                .venueId(UUID.fromString(venueId))
                .row(row)
                .column(col)
                .available(available)
                .metadata(metadata);
    }

    public Seat loadSeatInEvent(UUID eventId, UUID seatId) throws SQLException {
        String sql =
                "SELECT seatId, areaId, venueId, row, col, available, metadata, price, priceName, orderId FROM " +
                        "TKT.SKU WHERE seatId = ? AND eventId = ?";

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

    public List<Seat> loadSeatsInAreaOfEvent(UUID eventId, UUID areaId) throws SQLException {

        String sql =
                "SELECT seatId, areaId, venueId, row, col, available, metadata, price, priceName, orderId FROM " +
                "TKT.SKU WHERE eventId = ? AND areaId = ?";

        List<Seat> seats = new LinkedList<>();

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

            pstmt.setBigDecimal(4, BigDecimal.valueOf(price.getPrice(), 2));

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
                "AND venueId IN (SELECT venueId FROM TKT.Events WHERE id = ?) ";

        String sql2 =
                "INSERT INTO TKT.PricesDistribution (id, priceId, seatId, areaId, venueId) " +
                "SELECT ?, ?, NULL, NULL, TKT.Events.venueId FROM TKT.Prices " +
                "INNER JOIN TKT.Events ON TKT.Events.id = TKT.Prices.eventId " +
                "AND TKT.Events.id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, priceId.toString());
            pstmt.setString(2, eventId.toString());
            pstmt.setString(3, eventId.toString());

            if (pstmt.executeUpdate() < 1) {
                try (PreparedStatement pstmt2 = conn.prepareStatement(sql2)) {
                    pstmt2.setString(1, UUID.randomUUID().toString());
                    pstmt2.setString(2, priceId.toString());
                    pstmt2.setString(3, eventId.toString());

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
                        "AND venueId IS NULL ";

        String sql2 =
                "INSERT INTO TKT.PricesDistribution (id, priceId, seatId, areaId, venueId) " +
                        "VALUES (?, ?, ?, NULL, NULL)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, priceId.toString());
            pstmt.setString(2, eventId.toString());
            pstmt.setString(3, seatId.toString());

            if (pstmt.executeUpdate() < 1) {
                try (PreparedStatement pstmt2 = conn.prepareStatement(sql2)) {
                    pstmt2.setString(1, UUID.randomUUID().toString());
                    pstmt2.setString(2, priceId.toString());
                    pstmt2.setString(3, seatId.toString());

                    pstmt2.executeUpdate();
                }
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
                        "AND venueId IS NULL ";

        String sql2 =
                "INSERT INTO TKT.PricesDistribution (id, priceId, seatId, areaId, venueId) " +
                        "VALUES (?, ?, NULL, ?, NULL)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, priceId.toString());
            pstmt.setString(2, eventId.toString());
            pstmt.setString(3, areaId.toString());

            if (pstmt.executeUpdate() < 1) {
                try (PreparedStatement pstmt2 = conn.prepareStatement(sql2)) {
                    pstmt2.setString(1, UUID.randomUUID().toString());
                    pstmt2.setString(2, priceId.toString());
                    pstmt2.setString(3, areaId.toString());

                    pstmt2.executeUpdate();
                }
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

    @Transactional(rollbackFor = SQLException.class)
    public void saveNewOrder(Order order) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            String sqlNewOrder = "INSERT INTO TKT.ORDERS(ID, EVENTID, USERID, TIMESTAMP, METADATA) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sqlNewOrder)) {
                stmt.setString(1, order.getId() == null ? UUID.randomUUID().toString(): order.getId().toString());
                stmt.setString(2, order.getEventId().toString());
                stmt.setString(3, order.getUserId());
                stmt.setString(4, metadataMapToJsonString(order.getMetadata()));
                stmt.executeUpdate();
            }

            String sqlNewOrderSeat = "INSERT INTO TKT.ORDERSEATS(ORDERID, EVENTID, SEATID, METADATA) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sqlNewOrderSeat)) {
                for (OrderSeatsInner seatEntry: order.getSeats()) {
                    stmt.setString(1, order.getId().toString());
                    stmt.setString(2, order.getEventId().toString());
                    stmt.setString(3, seatEntry.getSeatId().toString());
                    stmt.setString(4, metadataMapToJsonString(seatEntry.getMetadata()));
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
                    .userId(rsOrders.getString("userId"))
                    .timestamp(rsOrders.getTimestamp("timeStamp"))
                    .paidAmount(rsOrders.getBigDecimal("paidAmount").multiply(BigDecimal.valueOf(100L)).intValue())
                    .metadata(parseMetadata(rsOrders.getString("metadata")));

            String sqlSeats = "SELECT ORDERID, EVENTID, SEATID, METADATA FROM TKT.ORDERSEATS " +
                    "WHERE ORDERID = ?";

            try (PreparedStatement stmtSeat = conn.prepareStatement(sqlSeats)) {
                stmtSeat.setString(1, order.getId().toString());
                ResultSet rsSeats = stmtSeat.executeQuery();

                while (rsSeats.next()) {
                    order.getSeats().add(
                            new OrderSeatsInner()
                                    .seatId(UUID.fromString(rsSeats.getString("seatId")))
                                    .metadata(parseMetadata(rsSeats.getString("metadata"))));
                }
            }
            loadOrders.add(order);
        }
        return loadOrders;
    }


    public List<Order> loadOrders(String userId, Date startTime, Date endTime) throws SQLException {

        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, USERID, TIMESTAMP, PAIDAMOUNT, METADATA FROM TKT.ORDERS " +
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
            String sqlOrders = "SELECT ID, EVENTID, USERID, TIMESTAMP, PAIDAMOUNT, METADATA FROM TKT.ORDERS " +
                    "WHERE TIMESTAMP BETWEEN ? AND ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                stmt.setTimestamp(2, new Timestamp(startTime.getTime()));
                stmt.setTimestamp(3, new Timestamp(endTime.getTime()));
                ResultSet rsOrders = stmt.executeQuery();

                return loadOrders(conn, rsOrders);
            }
        }

    }

    public List<Order> loadOrders(String userId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, USERID, TIMESTAMP, PAIDAMOUNT, METADATA FROM TKT.ORDERS " +
                    "WHERE USERID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                stmt.setString(1, userId);
                ResultSet rsOrders = stmt.executeQuery();

                return loadOrders(conn, rsOrders);
            }
        }
    }

    public List<Order> loadOrders() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, USERID, TIMESTAMP, PAIDAMOUNT, METADATA FROM TKT.ORDERS";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                ResultSet rsOrders = stmt.executeQuery();
                return loadOrders(conn, rsOrders);
            }
        }
    }

    public Order loadOrder(UUID orderId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlOrders = "SELECT ID, EVENTID, USERID, TIMESTAMP, PAIDAMOUNT, METADATA FROM TKT.ORDERS " +
                    "WHERE ID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlOrders)) {
                stmt.setString(1, orderId.toString());
                ResultSet rsOrders = stmt.executeQuery();

                return loadOrders(conn, rsOrders).get(0);
            }
        }
    }

    @Transactional(rollbackFor = SQLException.class)
    public void updateOrderPayAmount(UUID orderId, Integer paidAmount) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE TKT.ORDERS SET PAIDAMOUNT = PAIDAMOUNT + ? WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBigDecimal(1, BigDecimal.valueOf(paidAmount, 2));
                stmt.setString(2, orderId.toString());
                stmt.executeUpdate();
            }
        }
    }

    @Transactional
    public void updateOrderMetadata(Order order) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String sqlUpdateOrder = "UPDATE TKT.ORDERS SET METADATA = ? WHERE ID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpdateOrder)) {
                stmt.setString(1, metadataMapToJsonString(order.getMetadata()));
                stmt.setString(1, order.getId().toString());
                stmt.executeUpdate();
            }

            String sqlUpdateSeat = "UPDATE TKT.ORDERSEATS SET METADATA = ? " +
                    "WHERE ORDERID = ? AND EVENTID = ? AND SEATID = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpdateSeat)) {
                for (OrderSeatsInner seatEntry: order.getSeats()) {
                    stmt.setString(1, metadataMapToJsonString(seatEntry.getMetadata()));
                    stmt.setString(2, order.getId().toString());
                    stmt.setString(3, order.getEventId().toString());
                    stmt.setString(4, seatEntry.getSeatId().toString());
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
                            .startTime(rs.getDate("startTime"))
                            .endTime(rs.getDate("endDate"))
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

            stmt.setString(1, eventId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new Session().id(UUID.fromString(rs.getString("id")))
                            .eventId(UUID.fromString(rs.getString("eventId")))
                            .name(rs.getString("name"))
                            .startTime(rs.getDate("startTime"))
                            .endTime(rs.getDate("endDate"))
                            .metadata(parseMetadata(rs.getString("metadata")));
                } else
                    return null;
            }
        }
    }
}
