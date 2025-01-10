package com.jticket.endpoints;

import java.sql.SQLException;
import java.util.UUID;

import com.jticket.api.VenuesApi;
import com.jticket.api.model.Area;
import com.jticket.api.model.Seat;
import com.jticket.api.model.Venue;
import com.jticket.persist.SeatsRepository;
import com.jticket.persist.VenuesRepository;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class VenuesApiResource implements VenuesApi {

	@Inject
	private VenuesRepository repository;

	@Inject
	private SeatsRepository seatsRepository;

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
			Venue venue = repository.loadVenue(venueId);
			if (venue != null)
				return Response.ok(venue).build();
			else
				throw new WebApplicationException(Response.Status.NOT_FOUND);

		} catch (SQLException e) {
			throw new BadRequestException(e.getMessage());
		}
	}

	@Override
	public Response getAreaInVenue(UUID venueId, UUID areaId) {
		try {
			Area area = repository.loadArea(areaId.toString());
			if (area != null)
				return Response.ok(area).build();
			else
				throw new WebApplicationException(Response.Status.NOT_FOUND);

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

	@Override
	public Response getSeatInVenue(UUID venueId, UUID seatId) {
		try {
			Seat seat = seatsRepository.loadSeatInVenue(venueId, seatId);
			if (seat != null)
				return Response.ok(seat).build();
			else
				throw new WebApplicationException(Response.Status.NOT_FOUND);
		} catch (SQLException e) {
			throw new BadRequestException(e.getMessage());
		}
	}

	@Override
	public Response getSeatsInVenue(UUID venueId, UUID areaId) {

		if (areaId != null)
			return getAllSeatsInAreaOfVenue(venueId, areaId);
		else
			try {
				return Response.ok(seatsRepository.loadSeatsByVenue(venueId)).build();
			} catch (SQLException e) {
				throw new BadRequestException(e.getMessage());
			}
	}
}
