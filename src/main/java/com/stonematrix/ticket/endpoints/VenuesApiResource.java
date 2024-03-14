package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.VenuesApi;
import com.stonematrix.ticket.persist.VenuesRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

import java.sql.SQLException;
import java.util.UUID;

public class VenuesApiResource implements VenuesApi {

    @Inject
    private VenuesRepository repository;

    @Override
    public Response getAllAreasInVenue(UUID venueId) {
        try {
            return Response.ok(repository.loadAreas(venueId.toString())).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getAllSeatsInAreaOfVenue(UUID venueId, UUID areaId) {

        try {
            return Response.ok(repository.loadSeats(venueId, areaId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getAllVenues() {
        try {
            return Response.ok(repository.loadAllVenues()).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getVenue(UUID venueId) {
       try {
            return Response.ok(repository.loadVenue(venueId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getAreaInVenue(UUID venueId, UUID areaId) {
        try {
            return Response.ok(repository.loadArea(areaId.toString())).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getVenueSvgLayout(UUID venueId) {
        try {
            return Response.ok(repository.loadVenueSvg(venueId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
