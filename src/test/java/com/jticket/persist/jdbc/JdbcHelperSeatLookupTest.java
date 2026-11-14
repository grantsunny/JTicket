package com.jticket.persist.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.jticket.api.model.Seat;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcHelperSeatLookupTest {
    @Test
    void findsSeatOnlyInItsVenue() throws Exception {
        var dataSource = new DriverManagerDataSource(
                "jdbc:derby:memory:jdbc-seat-lookup-" + UUID.randomUUID() + ";create=true", "TKT", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA TKT");
        jdbc.execute("CREATE TABLE TKT.Areas (id VARCHAR(36) PRIMARY KEY, venueId VARCHAR(36))");
        jdbc.execute("CREATE TABLE TKT.Seats (id VARCHAR(36) PRIMARY KEY, areaId VARCHAR(36), row INT, col INT, available BOOLEAN, metadata CLOB)");
        UUID venueId = UUID.randomUUID(), areaId = UUID.randomUUID(), seatId = UUID.randomUUID();
        jdbc.update("INSERT INTO TKT.Areas(id,venueId) VALUES (?,?)", areaId.toString(), venueId.toString());
        jdbc.update("INSERT INTO TKT.Seats(id,areaId,row,col,available,metadata) VALUES (?,?,2,3,FALSE,?)",
                seatId.toString(), areaId.toString(), "{\"section\":\"balcony\"}");
        var helper = new JdbcHelper(dataSource);

        Seat seat = helper.loadSeatInVenue(venueId, seatId);

        assertThat(seat).isNotNull();
        assertThat(seat.getId()).isEqualTo(seatId);
        assertThat(seat.getVenueId()).isEqualTo(venueId);
        assertThat(seat.getAreaId()).isEqualTo(areaId);
        assertThat(seat.getRow()).isEqualTo(2);
        assertThat(seat.getCol()).isEqualTo(3);
        assertThat(seat.getAvailable()).isFalse();
        assertThat(seat.getMetadata()).containsEntry("section", "balcony");
        assertThat(helper.loadSeatInVenue(UUID.randomUUID(), seatId)).isNull();
        assertThat(helper.loadSeatInVenue(venueId, UUID.randomUUID())).isNull();
    }
}
