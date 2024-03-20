package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.EventsApi;
import com.stonematrix.ticket.api.model.*;
import com.stonematrix.ticket.persist.EventsRepository;
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
    private EventsRepository repository;


    @Override
    public Response listTicketPricesOfEvent(UUID eventId) {
        try {
            List<Price> prices = repository.loadPrices(eventId);
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
            Price price = repository.loadSeatLevelPricingOfEvent(eventId, seatId);
            if (price == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(price).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getSession(UUID eventId, UUID sessionId) {
        return null;
    }


    @Override
    public Response getSeatInEvent(UUID eventId, UUID seatId) {
        try {
            Seat seat = repository.loadSeatInEvent(eventId, seatId);
            if (seat == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(seat).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }


    @Override
    public Response getAllAreasInEvent(UUID eventId) {
        try {
            List<Area> areas = repository.loadAllAreasInEvent(eventId);
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
            List<Seat> seats = repository.loadSeatsInAreaOfEvent(eventId, areaId);
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
        try {
            Area area = repository.loadAreaInEvent(eventId, areaId);
            if (area == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(area).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }



    @Override
    public Response getAreaLevelPricingOfEvent(UUID eventId, UUID areaId) {
        try {
            Price price = repository.loadAreaLevelPricingOfEvent(eventId, areaId);
            if (price == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(price).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getDefaultPricingOfEvent(UUID eventId) {
        try {
            Price price = repository.loadDefaultPricingOfEvent(eventId);
            if (price == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(price).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response createTicketPriceOfEvent(UUID eventId, Price price) {
        try {
            price = price.id(UUID.randomUUID());
            repository.saveTicketPriceOfEvent(eventId, price);

            return Response.created(
                    uriInfo.getRequestUriBuilder()
                            .path(price.getId().toString())
                            .build())
                    .build();

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

    @Override
    public Response deleteEvent(UUID eventId) {
        try {
            repository.deleteEvent(eventId);
            return Response.status(Response.Status.NO_CONTENT).build();

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

    @Override
    public Response getTicketPriceOfEvent(UUID eventId, UUID priceId) {
        try {
            Price price = repository.loadPriceOfEventById(eventId, priceId);
            if (price == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(price).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response deleteTicketPriceOfEvent(UUID eventId, UUID priceId) {
        try {
            repository.deleteTicketPriceOfEvent(eventId, priceId);
            return Response.status(Response.Status.NO_CONTENT).build();

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

    @Override
    public Response assignDefaultPricingOfEvent(UUID eventId, LinkPrice linkPrice) {

        UUID priceId = linkPrice.getPriceId();
        try {
            repository.saveDefaultPricingOfEvent(eventId, priceId);
            return Response.accepted().build();

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

    @Override
    public Response assignSeatLevelPricingOfEvent(UUID eventId, UUID seatId, LinkPrice linkPrice) {
        UUID priceId = linkPrice.getPriceId();
        try {
            repository.saveSeatLevelPricingOfEvent(eventId, seatId, priceId);
            return Response.accepted().build();

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

    @Override
    public Response assignAreaLevelPricingOfEvent(UUID eventId, UUID areaId, LinkPrice linkPrice) {
        UUID priceId = linkPrice.getPriceId();
        try {
            repository.saveAreaLevelPricingOfEvent(eventId, areaId, priceId);
            return Response.accepted().build();

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


    @Override
    public Response getEventOrders(UUID eventId) {
        return null;
    }


    @Override
    public Response createEvent(Event event, String xCopyFromId) {
        if (xCopyFromId == null)
            return createEvent(event);
        else {
            event = event.id(UUID.randomUUID());
            try {
                repository.saveEventAndCopyPrices(event, xCopyFromId);
                UriBuilder uriBuilder =
                        uriInfo.getRequestUriBuilder().
                                path(String.valueOf(event.getId()));

                return Response.created(uriBuilder.build()).build();
            } catch (SQLException e) {
                throw new BadRequestException(e);
            }
        }
    }

    private Response createEvent(Event event) {
        event = event.id(UUID.randomUUID());
        try {
            repository.saveEvent(event);
            UriBuilder uriBuilder =
                    uriInfo.getRequestUriBuilder().
                            path(String.valueOf(event.getId()));

            return Response.created(uriBuilder.build()).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getEventById(UUID eventId) {
        try {
            Event event = repository.loadEvent(eventId);
            if (event == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(event).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response listEvents(String venueId) {
        if (venueId != null) {
            try {
                List<Event> events = repository.loadEventsByVenue(venueId);
                return Response.ok(events).build();
            } catch (SQLException e) {
                throw new BadRequestException(e);
            }
        } else
            return listEvents();
    }


    private Response listEvents() {
        try {
            List<Event> events = repository.loadAllEvents();
            return Response.ok(events).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response getVenueOfEvent(UUID eventId) {
        try {
            Venue venue = repository.loadVenueByEvent(eventId);
            if (venue == null)
                return Response.status(Response.Status.NOT_FOUND).build();
            else
                return Response.ok(venue).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }



    @Override
    public Response getEventVenueSvgLayout(UUID eventId) {
        try {
            return Response.ok(repository.loadEventVenueSvg(eventId)).build();
        } catch (SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    public Response assignVenueToEvent(UUID eventId, LinkVenue linkVenue) {
        UUID venueId = linkVenue.getVenueId();
        try {
            repository.updateVenueOfEvent(eventId, venueId);
            return Response.accepted().build();
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

    @Override
    public Response updateEvent(UUID eventId, Event event) {
        try {
            repository.updateEvent(eventId, event);
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

    @Override
    public Response createSession(UUID eventId, Session session) {
        return null;
    }

    @Override
    public Response listSessions(UUID eventId) {
        return null;
    }

    @Override
    public Response updateSession(UUID eventId, UUID sessionId, Session session) {
        return null;
    }

    @Override
    public Response deleteSession(UUID eventId, UUID sessionId) {
        return null;
    }
}
