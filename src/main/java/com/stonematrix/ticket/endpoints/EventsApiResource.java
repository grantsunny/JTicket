package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.EventsApi;
import com.stonematrix.ticket.api.model.Event;
import com.stonematrix.ticket.api.model.LinkEvent;
import com.stonematrix.ticket.api.model.Venue;
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
    public Response linkEventVenue(UUID eventId, LinkEvent linkEvent) {
        UUID venueId = linkEvent.getVenueId();
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

    @Override
    public Response getEventOrders(UUID eventId) {
        return null;
    }

}
