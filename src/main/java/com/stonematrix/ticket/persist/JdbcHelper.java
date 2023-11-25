package com.stonematrix.ticket.persist;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonematrix.ticket.api.model.Area;
import com.stonematrix.ticket.api.model.Seat;
import com.stonematrix.ticket.api.model.Venue;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

@Component
public class JdbcHelper {

    @Inject
    private DataSource dataSource;

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
}
