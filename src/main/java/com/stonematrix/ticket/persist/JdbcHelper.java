package com.stonematrix.ticket.persist;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stonematrix.ticket.api.model.Venue;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JdbcHelper {

    @Inject
    private DataSource dataSource;

    public Venue loadVenue(UUID venueId) throws SQLException {
        String name;
        Map<String, Object> metadata;

        String sql = "SELECT id, name, metadata FROM TKT.Venues WHERE id = ?";
        Connection conn = dataSource.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, venueId.toString());

        ResultSet rs = pstmt.executeQuery();
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

    public void saveVenue(Venue venue) throws SQLException {
        String sql = "INSERT INTO TKT.Venues (id, name, metadata) VALUES (?, ?, ?)";

        Connection conn = dataSource.getConnection();
        PreparedStatement pstmt = conn.prepareStatement(sql);
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
