package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.VenuesApi;
import com.stonematrix.ticket.api.model.Area;
import com.stonematrix.ticket.api.model.Seat;
import com.stonematrix.ticket.api.model.Venue;
import com.stonematrix.ticket.persist.JdbcHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class VenuesApiResource implements VenuesApi {

    @Inject
    private JdbcHelper jdbc;

    @Override
    public List<Area> getAllAreasInVenue(UUID venueId) {
        try {
            return jdbc.loadAreas(venueId.toString());
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public List<Seat> getAllSeatsInAreaOfVenue(UUID venueId, UUID areaId) {

        try {
            return jdbc.loadSeats(venueId, areaId);
        } catch (SQLException e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public List<Venue> getAllVenues() {
        try {
            return jdbc.loadAllVenues();
        } catch (SQLException e) {
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    public Venue getVenue(UUID venueId) {
       try {
            return jdbc.loadVenue(venueId);
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Area getAreaInVenue(UUID venueId, UUID areaId) {
        try {
            return jdbc.loadArea(areaId.toString());
        } catch (IOException | SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public File getVenueSvgLayout(UUID venueId) {
        try {
            return jdbc.loadVenueSvg(venueId);
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

}
