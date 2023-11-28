package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.SeatsApi;
import com.stonematrix.ticket.persist.JdbcHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

import java.sql.SQLException;
import java.util.UUID;

public class SeatsApiResource implements SeatsApi {

    @Inject
    private JdbcHelper jdbc;

    @Override
    public Response getAllSeats(UUID venueId, UUID areaId) {
        try {
            return Response.ok(jdbc.loadSeats(venueId, areaId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getSeat(UUID seatId) {
        try {
            return Response.ok(jdbc.loadSeat(seatId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
