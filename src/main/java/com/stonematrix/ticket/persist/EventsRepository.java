package com.stonematrix.ticket.persist;

import com.stonematrix.ticket.api.model.*;

import java.io.File;
import java.util.List;
import java.util.UUID;

public interface EventsRepository {

    Price loadSeatLevelPricingOfEvent(UUID eventId, UUID seatId) throws PersistenceException;
    List<Price> loadPrices(UUID eventId) throws PersistenceException;
    Seat loadSeatInEvent(UUID eventId, UUID seatId) throws PersistenceException;
    List<Area> loadAllAreasInEvent(UUID eventId) throws PersistenceException;
    List<Seat> loadSeatsInAreaOfEvent(UUID eventId, UUID areaId) throws PersistenceException;
    Area loadAreaInEvent(UUID eventId, UUID areaId) throws PersistenceException;
    Price loadAreaLevelPricingOfEvent(UUID eventId, UUID areaId) throws PersistenceException;
    Price loadDefaultPricingOfEvent(UUID eventId) throws PersistenceException;
    void saveTicketPriceOfEvent(UUID eventId, Price price) throws PersistenceException;
    void deleteEvent(UUID eventId) throws PersistenceException;
    Price loadPriceOfEventById(UUID eventId, UUID priceId) throws PersistenceException;
    void deleteTicketPriceOfEvent(UUID eventId, UUID priceId) throws PersistenceException;
    void saveDefaultPricingOfEvent(UUID eventId, UUID priceId) throws PersistenceException;
    void saveSeatLevelPricingOfEvent(UUID eventId, UUID seatId, UUID priceId) throws PersistenceException;
    void saveAreaLevelPricingOfEvent(UUID eventId, UUID areaId, UUID priceId) throws PersistenceException;
    void saveEvent(Event event) throws PersistenceException;
    Event loadEvent(UUID eventId) throws PersistenceException;
    List<Event> loadEventsByVenue(String venueId) throws PersistenceException;
    List<Event> loadAllEvents() throws PersistenceException;
    Venue loadVenueByEvent(UUID eventId) throws PersistenceException;
    File loadEventVenueSvg(UUID eventId) throws PersistenceException;
    void updateVenueOfEvent(UUID eventId, UUID venueId) throws PersistenceException;
    void updateEvent(UUID eventId, Event event) throws PersistenceException;
    void saveEventAndCopyPrices(Event event, String copyFromEventId) throws PersistenceException;
}
