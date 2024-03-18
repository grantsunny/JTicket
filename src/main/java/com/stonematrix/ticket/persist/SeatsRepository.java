package com.stonematrix.ticket.persist;

import com.stonematrix.ticket.api.model.Seat;

import java.util.List;
import java.util.UUID;

public interface SeatsRepository {
    List<Seat> loadSeats(UUID venueId, UUID areaId) throws PersistenceException;
    Seat loadSeat(UUID seatId) throws PersistenceException;
    void saveSeats(List<Seat> seats) throws PersistenceException;
    List<Seat> loadSeatsByVenue(UUID venueId) throws PersistenceException;

}
