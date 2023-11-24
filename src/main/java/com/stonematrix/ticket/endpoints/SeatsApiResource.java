package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.SeatsApi;
import com.stonematrix.ticket.api.model.Seat;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;
import java.util.UUID;

public class SeatsApiResource implements SeatsApi {
    @Context
    private UriInfo uriInfo;

    @Override
    public List<Seat> getAllSeats(UUID venueId, UUID areaId) {
        return null;
    }

    @Override
    public Seat getSeat(UUID seatId) {
        return null;
    }
}
