package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.VenuesApi;
import com.stonematrix.ticket.persist.JdbcHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

public class VenuesApiResource implements VenuesApi {

    @Inject
    private JdbcHelper jdbc;

    @Override
    public Response getAllAreasInVenue(UUID venueId) {
        try {
            return Response.ok(jdbc.loadAreas(venueId.toString())).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getAllSeatsInAreaOfVenue(UUID venueId, UUID areaId) {

        try {
            return Response.ok(jdbc.loadSeats(venueId, areaId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getAllVenues() {
        try {
            return Response.ok(jdbc.loadAllVenues()).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getVenue(UUID venueId) {
       try {
            return Response.ok(jdbc.loadVenue(venueId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getAreaInVenue(UUID venueId, UUID areaId) {
        try {
            return Response.ok(jdbc.loadArea(areaId.toString())).build();
        } catch (IOException | SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response getVenueSvgLayout(UUID venueId) {
        try {
            return Response.ok(jdbc.loadVenueSvg(venueId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
