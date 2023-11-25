package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.SeatsApi;
import com.stonematrix.ticket.api.model.Seat;
import com.stonematrix.ticket.persist.JdbcHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class SeatsApiResource implements SeatsApi {

    @Inject
    private JdbcHelper jdbc;

    @Override
    public List<Seat> getAllSeats(UUID venueId, UUID areaId) {
        try {
            return jdbc.loadSeats(venueId, areaId);
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Seat getSeat(UUID seatId) {
        try {
            return jdbc.loadSeat(seatId);
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
