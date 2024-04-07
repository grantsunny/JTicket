package com.jticket.persist;

import com.jticket.api.model.Area;
import com.jticket.api.model.Seat;
import com.jticket.api.model.Venue;

import java.io.File;
import java.util.List;
import java.util.UUID;

public interface VenuesRepository {
    List<Venue> loadAllVenues() throws PersistenceException;
    List<Area> loadAreas(String venueId) throws PersistenceException;
    List<Seat> loadSeats(UUID venueId, UUID areaId) throws PersistenceException;
    Venue loadVenue(UUID venueId) throws PersistenceException;
    Area loadArea(String areaId) throws PersistenceException;
    File loadVenueSvg(UUID venueId) throws PersistenceException;
    void saveVenue(Venue venue, String svg) throws PersistenceException;
    void saveAreas(List<Area> areas) throws PersistenceException;
}