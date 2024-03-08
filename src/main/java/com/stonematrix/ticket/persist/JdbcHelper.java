package com.stonematrix.ticket.persist;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonematrix.ticket.api.model.*;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.Date;

@Component
public class JdbcHelper {

    @Inject
    private DataSource dataSource;

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

    public void saveVenue(Venue venue) throws SQLException {
        String sql = "INSERT INTO TKT.Venues (id, name, metadata) VALUES (?, ?, ?)";

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

    public List<Seat> loadSeats(UUID venueId) throws SQLException {
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
            return loadSeats(venueId);

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
        String sql = "INSERT INTO TKT.Events (id, name, venueId, startTime, endTime, metadata) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, event.getId().toString());
            stmt.setString(2, event.getName());

            UUID venueId;
            if ((venueId = event.getVenueId()) != null)
                stmt.setString(3, venueId.toString());
            else
                stmt.setNull(3, Types.VARCHAR);

            stmt.setTimestamp(4, new Timestamp(event.getStartTime().getTime()));
            stmt.setTimestamp(5, new Timestamp(event.getEndTime().getTime()));

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
        String sql = "SELECT venueId, name, startTime, endTime, metadata FROM TKT.Events WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (eventId == null)
                throw new SQLException("Specified UUID eventId is unexpected null");

            pstmt.setString(1, eventId.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String venueId = rs.getString("venueId");
                    String name = rs.getString("name");

                    Date startTime = rs.getTimestamp("startTime");
                    Date endTime = rs.getTimestamp("endTime");

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
                            .startTime(startTime)
                            .endTime(endTime)
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
                String id= rs.getString("id");
                String venueId = rs.getString("venueId");
                String name = rs.getString("name");

                Date startTime = rs.getTimestamp("startTime");
                Date endTime = rs.getTimestamp("endTime");

                Map<String, Object> metadata;
                try {
                    metadata = new ObjectMapper().readValue(
                            rs.getString("metadata"),
                            Map.class);
                } catch (JsonProcessingException ex) {
                    metadata = new HashMap<>();
                }

                events.add(new Event()
                        .id(UUID.fromString(id))
                        .venueId(UUID.fromString(venueId))
                        .name(name)
                        .startTime(startTime)
                        .endTime(endTime)
                        .metadata(metadata));
            }
        }
        return events;
    }

    public List<Event> loadEventsByVenue(String venueId) throws SQLException {
        String sql = "SELECT id, venueId, name, startTime, endTime, metadata FROM TKT.Events " +
                "WHERE venueId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, venueId);
            return loadEvents(stmt);
        }
    }

    public List<Event> loadAllEvents() throws SQLException {
        String sql = "SELECT id, venueId, name, startTime, endTime, metadata FROM TKT.Events";
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
                "startTime = ?, " +
                "endTime = ?, " +
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
            pstmt.setTimestamp(3, new Timestamp(event.getStartTime().getTime()));
            pstmt.setTimestamp(4, new Timestamp(event.getEndTime().getTime()));

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

    public Seat loadSeatInEvent(UUID eventId, UUID seatId) throws SQLException {
        String sql =
                "SELECT row, col, available, metadata, price, booked FROM " +
                        "TKT.SKU WHERE seatId = ? AND eventId = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, seatId.toString());
            pstmt.setString(2, eventId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {

                    int row = rs.getInt("row");
                    int col = rs.getInt("col");
                    boolean available = rs.getBoolean("available");
                    boolean booked = rs.getBoolean("booked");
                    BigDecimal price = rs.getBigDecimal("price");
                    String metadata = rs.getString("metadata");

                    return new Seat().id(seatId)
                            .row(row)
                            .column(col)
                            .available(available)
                            .booked(booked)
                            .price((price == null) ? null : price.multiply(BigDecimal.valueOf(100L)).intValue())
                            .metadata(parseMetadata(metadata));
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

    public List<Seat> loadSeatsInAreaOfEvent(UUID eventId, UUID areaId) throws SQLException {

        String sql =
                "SELECT seatId, row, col, venueId, available, metadata, price, booked FROM " +
                "TKT.SKU WHERE eventId = ? AND areaId = ?";

        List<Seat> seats = new LinkedList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId.toString());
            pstmt.setString(2, areaId.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("seatId");
                    int row = rs.getInt("row");
                    int col = rs.getInt("col");
                    boolean available = rs.getBoolean("available");
                    boolean booked = rs.getBoolean("booked");
                    BigDecimal price = rs.getBigDecimal("price");
                    String metadata = rs.getString("metadata");

                    seats.add(new Seat().id(UUID.fromString(id))
                            .row(row)
                            .column(col)
                            .available(available)
                            .booked(booked)
                            .price((price == null) ? null : price.multiply(BigDecimal.valueOf(100L)).intValue())
                            .metadata(parseMetadata(metadata)));
                }
            }
        }
        return seats;
    }

    public Area loadAreaInEvent(UUID eventId, UUID areaId) throws SQLException {
        String sql = "SELECT venueId, name, metadata FROM TKT.Areas " +
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

    public void deleteEvent(UUID eventId) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String[] sqlCleanUp = {
                    "DELETE FROM TKT.PricesDistribution WHERE priceId IN (SELECT id FROM TKT.Prices WHERE eventId = ?)",
                    "DELETE FROM TKT.Prices WHERE eventId = ?",
                    "DELETE FROM TKT.Events WHERE id = ?"
            };

            for (String sql: sqlCleanUp) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, eventId.toString());
                    pstmt.executeUpdate();
                }
            }
        }
    }



}
