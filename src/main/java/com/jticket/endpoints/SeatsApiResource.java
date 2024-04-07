package com.jticket.endpoints;

import com.jticket.persist.SeatsRepository;
import com.jticket.api.SeatsApi;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

import java.sql.SQLException;
import java.util.UUID;

public class SeatsApiResource implements SeatsApi {

    @Inject
    private SeatsRepository repository;

    @Override
    public Response getAllSeats(UUID venueId, UUID areaId) {
        try {
            return Response.ok(repository.loadSeats(venueId, areaId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getSeat(UUID seatId) {
        try {
            return Response.ok(repository.loadSeat(seatId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
