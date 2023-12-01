package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.EventsApi;
import com.stonematrix.ticket.api.model.*;
import com.stonematrix.ticket.persist.JdbcHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class EventsApiResource implements EventsApi {

    @Context
    private UriInfo uriInfo;

    @Inject
    private JdbcHelper jdbc;



    @Override
    public Response listTicketPricesOfEvent(UUID eventId) {
        try {
            List<Price> prices = jdbc.loadPrices(eventId);
            if ((prices == null) || (prices.isEmpty()))
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(prices).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getSeatLevelPricingOfEvent(UUID eventId, UUID seatId) {
        try {
            Price price = jdbc.loadSeatLevelPricingOfEvent(eventId, seatId);
            if (price == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(price).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getTicketPriceOfEvent(UUID eventId, UUID priceId) {
        try {
            Price price = jdbc.loadTicketPriceOfEvent(eventId, priceId);
            if (price == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(price).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }


    @Override
    public Response getSeatInEvent(UUID eventId, UUID seatId) {
        try {
            Seat seat = jdbc.loadSeatInEvent(eventId, seatId);
            if (seat == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(seat).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response deleteTicketPriceOfEvent(UUID eventId, UUID priceId) {
        try {
            jdbc.deleteTicketPriceOfEvent(eventId, priceId);
            return Response.status(Response.Status.NO_CONTENT).build();

        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getAllAreasInEvent(UUID eventId) {
        try {
            List<Area> areas = jdbc.loadAreasInEvent(eventId);
            if ((areas == null) || (areas.isEmpty()))
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(areas).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getAllSeatsInAreaOfEvent(UUID eventId, UUID areaId) {
        try {
            List<Seat> seats = jdbc.loadSeatsInAreaOfEvent(eventId, areaId);
            if ((seats == null) || (seats.isEmpty()))
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(seats).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getAreaInEvent(UUID eventId, UUID areaId) {
        return null;
    }

    @Override
    public Response getAreaLevelPricingOfEvent(UUID eventId, UUID areaId) {
        return null;
    }

    @Override
    public Response getDefaultPricingOfEvent(UUID eventId) {
        return null;
    }

    @Override
    public Response creatTicketPriceOfEvent(UUID eventId, Price price) {
        return null;
    }


    @Override
    public Response assignDefaultPricingOfEvent(UUID eventId, LinkPrice linkPrice) {
        return null;
    }

    @Override
    public Response assignSeatLevelPricingOfEvent(UUID eventId, UUID seatId, LinkPrice linkPrice) {
        return null;
    }

    @Override
    public Response assignVenueLevelPricingOfEvent(UUID eventId, UUID areaId, LinkPrice linkPrice) {
        return null;
    }


    @Override
    public Response getEventOrders(UUID eventId) {
        return null;
    }

    @Override
    public Response createEvent(Event event) {

        event = event.id(UUID.randomUUID());
        try {
            jdbc.saveEvent(event);
            UriBuilder uriBuilder =
                    uriInfo.getBaseUriBuilder().
                            path(EventsApi.class).
                            path(String.valueOf(event.getId()));

            return Response.created(uriBuilder.build()).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getEventById(UUID eventId) {
        try {
            Event event = jdbc.loadEvent(eventId);
            if (event == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(event).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }


    @Override
    public Response listEvents() {
        try {
            List<Event> events = jdbc.loadAllEvents();
            return Response.ok(events).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }


    @Override
    public Response getVenueOfEvent(UUID eventId) {
        try {
            Venue venue = jdbc.loadVenueByEvent(eventId);
            if (venue == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(venue).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response assignVenueToEvent(UUID eventId, LinkVenue linkVenue) {
        UUID venueId = linkVenue.getId();
        try {
            jdbc.updateVenueOfEvent(eventId, venueId);
            return Response.noContent().build();
        } catch (SQLException e) {
            switch (e.getSQLState()) {
                case "304":
                    return Response.notModified().build();
                case "23000":
                case "23505":
                    throw new ClientErrorException("Conflict occurred", Response.Status.CONFLICT);
                default:
                    throw new BadRequestException(e);
            }
        }
    }
}
