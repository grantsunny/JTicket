package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.VenuesApi;
import com.stonematrix.ticket.api.model.Area;
import com.stonematrix.ticket.api.model.Seat;
import com.stonematrix.ticket.api.model.Venue;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;


import java.io.File;
import java.util.List;
import java.util.UUID;

public class VenuesApiResource implements VenuesApi {

    @Context
    private UriInfo uriInfo;

    @Override
    public List<Area> getAllAreasInVenue(UUID venueId) {
        return null;
    }

    @Override
    public List<Seat> getAllSeatsInAreaOfVenue(UUID venueId, UUID areaId) {
        return null;
    }

    @Override
    public List<Venue> getAllVenues() {
        return null;
    }

    @Override
    public Area getAreaInVenue(UUID venueId, UUID areaId) {
        return null;
    }

    @Override
    public Venue getVenue(UUID venueId) {
        return null;
    }

    @Override
    public File getVenueSvgLayout(UUID venueId) {
        return null;
    }

}
