package com.jticket.endpoints;

import static com.jticket.security.OAuth2Scopes.EVENT_READ;
import static com.jticket.security.OAuth2Scopes.EVENT_WRITE;
import static com.jticket.security.OAuth2Scopes.ORDER_READ_ALL;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

import com.jticket.api.EventsApi;
import com.jticket.api.model.Area;
import com.jticket.api.model.Event;
import com.jticket.api.model.EventStatistics;
import com.jticket.api.model.LinkPrice;
import com.jticket.api.model.LinkVenue;
import com.jticket.api.model.Order;
import com.jticket.api.model.Price;
import com.jticket.api.model.Session;
import com.jticket.api.model.TicketingSeat;
import com.jticket.api.model.SessionStatistics;
import com.jticket.api.model.Venue;
import com.jticket.persist.EventsRepository;
import com.jticket.persist.OrdersRepository;
import com.jticket.persist.PersistenceException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

public class EventsApiResource implements EventsApi {

	@Context
	private UriInfo uriInfo;

	@Inject
	private EventsRepository repository;

	@Inject
	private OrdersRepository ordersRepository;

	@Value("${ticket.jwt.public-key}")
	private String publicKeyPem;

	@Value("${ticket.jwt.algorithm:RSA}")
	private String keyAlgorithm;

	private PublicKey publicKey;

	private PublicKey parsePublicKey() throws InvalidKeySpecException, NoSuchAlgorithmException {

		if (publicKey != null)
			return publicKey;

		byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "")
				.replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", ""));

		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
		PublicKey publicKey = KeyFactory.getInstance(keyAlgorithm).generatePublic(keySpec);

		this.publicKey = publicKey;
		return publicKey;
	}

	@Override
	@RolesAllowed(EVENT_WRITE)
	public Response checkIn(String token, String eventId, String sessionId) {
        Claims claims;
		try {
			claims = Jwts.parser()
			        .verifyWith(parsePublicKey()) // Set the public key to verify the signature
			        .build()
			        .parseSignedClaims(token)
			        .getPayload();
		} catch (JwtException | IllegalArgumentException | InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new WebApplicationException(e.getMessage(), e);
		}

        // Extract standard claims
        // String subject = claims.getSubject(); 		// "sub" field
        String issuer = claims.getIssuer(); 		// "iss" field
        Date expiration = claims.getExpiration(); 	// "exp" field
        
        Date now = new Date();
        
        if (!"JTicket".equals(issuer))
        	throw new NotAuthorizedException("Not recognized token - invalid issuer");

        if (expiration == null || expiration.before(now))
        	throw new NotAuthorizedException("Ticket token expired");
        
        if (!eventId.equals(claims.get("event", String.class)))
        	throw new NotAuthorizedException("Invalid ticket for specified event");
        
        if (!sessionId.equals(claims.get("session", String.class)))
        	throw new NotAuthorizedException("Invalid ticket for specified session");
        
        String seatId = claims.get("seat", String.class);
        
        // Update OrderSeat.checkedInTimestamp and return the session-context seat.
        
		try {
			UUID parsedEventId = UUID.fromString(eventId);
			UUID parsedSessionId = UUID.fromString(sessionId);
			UUID parsedSeatId = UUID.fromString(seatId);
			if (repository.checkInSeat(parsedEventId, parsedSessionId, parsedSeatId) < 1)
				throw new BadRequestException("Ticket was already checked in or does not identify a purchased seat");

			TicketingSeat seat = repository.loadSeatInSession(parsedEventId, parsedSessionId, parsedSeatId);
			if (seat == null)
				throw new BadRequestException("Ticket seat does not exist in the specified session");

			return Response.ok(seat).build();
		} catch (IllegalArgumentException e) {
			throw new BadRequestException("Ticket contains an invalid identifier", e);
		} catch (PersistenceException e) {
			throw new WebApplicationException(e.getMessage(), e);
		}
	}

	@Override
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_READ)
	public Response getSeatInEvent(UUID eventId, UUID seatId) {
		try {
			TicketingSeat seat = repository.loadSeatInEvent(eventId, seatId);
			if (seat == null)
				return Response.status(Response.Status.NOT_FOUND).build();
			else
				return Response.ok(seat).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

	@Override
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_READ)
	public Response getAllSeatsInAreaOfEvent(UUID eventId, UUID areaId) {
		try {
			List<TicketingSeat> seats = repository.loadSeatsInAreaOfEvent(eventId, areaId);
			if ((seats == null) || (seats.isEmpty()))
				return Response.status(Response.Status.NOT_FOUND).build();
			else
				return Response.ok(seats).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

	@Override
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_WRITE)
	public Response createTicketPriceOfEvent(UUID eventId, Price price) {
		try {
			price = price.id(UUID.randomUUID());
			repository.saveTicketPriceOfEvent(eventId, price);

			return Response.created(uriInfo.getRequestUriBuilder().path(price.getId().toString()).build()).build();

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
	@RolesAllowed(EVENT_WRITE)
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
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_WRITE)
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
	@RolesAllowed(EVENT_WRITE)
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
	@RolesAllowed(EVENT_WRITE)
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
	@RolesAllowed(EVENT_WRITE)
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
	@RolesAllowed(ORDER_READ_ALL)
	public Response getEventOrders(UUID eventId) {
		try {
			if (repository.loadEvent(eventId) == null)
				return Response.status(Response.Status.NOT_FOUND).build();

			List<Order> orders = ordersRepository.loadOrders(eventId);
			if (orders == null)
				orders = List.of();
			return Response.ok(orders).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

	@Override
	@RolesAllowed(EVENT_READ)
	public Response getEventStatistics(UUID eventId) {
		try {
			if (repository.loadEvent(eventId) == null)
				return Response.status(Response.Status.NOT_FOUND).build();

			EventStatistics statistics = repository.loadEventStatistics(eventId);
			return Response.ok(statistics).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

	@Override
	@RolesAllowed(EVENT_READ)
	public Response getSessionStatistics(UUID eventId, UUID sessionId) {
		try {
			if (repository.loadSession(eventId, sessionId) == null)
				return Response.status(Response.Status.NOT_FOUND).build();

			SessionStatistics statistics = repository.loadSessionStatistics(eventId, sessionId);
			return Response.ok(statistics).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

	@Override
	@RolesAllowed(EVENT_READ)
	public Response getAllSeatsInAreaOfSession(UUID eventId, UUID sessionId, UUID areaId) {
		try {
			if (repository.loadSession(eventId, sessionId) == null || repository.loadAreaInEvent(eventId, areaId) == null)
				return Response.status(Response.Status.NOT_FOUND).build();

			List<TicketingSeat> seats = repository.loadSeatsInAreaOfSession(eventId, sessionId, areaId);
			if ((seats == null) || seats.isEmpty())
				return Response.status(Response.Status.NOT_FOUND).build();
			return Response.ok(seats).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

	@Override
	@RolesAllowed(EVENT_WRITE)
	public Response createEvent(Event event, String xCopyFromId) {
		if (xCopyFromId == null)
			return createEvent(event);
		else {
			event = event.id(UUID.randomUUID());
			try {
				repository.saveEventAndCopyPrices(event, xCopyFromId);
				UriBuilder uriBuilder = uriInfo.getRequestUriBuilder().path(String.valueOf(event.getId()));

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
			UriBuilder uriBuilder = uriInfo.getRequestUriBuilder().path(String.valueOf(event.getId()));

			return Response.created(uriBuilder.build()).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

	@Override
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_READ)
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
	@RolesAllowed(EVENT_READ)
	public Response getEventVenueSvgLayout(UUID eventId) {
		try {
			return Response.ok(repository.loadEventVenueSvg(eventId)).build();
		} catch (SQLException e) {
			throw new BadRequestException(e.getMessage());
		}
	}

	@Override
	@RolesAllowed(EVENT_WRITE)
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
	@RolesAllowed(EVENT_WRITE)
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
	@RolesAllowed(EVENT_WRITE)
	public Response createSession(UUID eventId, Session session) {
		session = session.id(UUID.randomUUID());
		try {
			repository.saveSession(eventId, session);
			UriBuilder uriBuilder = uriInfo.getRequestUriBuilder().path(String.valueOf(session.getId()));

			return Response.created(uriBuilder.build()).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

	@Override
	@RolesAllowed(EVENT_READ)
	public Response listSessions(UUID eventId) {
		try {
			List<Session> sessions = repository.loadSessions(eventId);
			return Response.ok(sessions).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

	@Override
	@RolesAllowed(EVENT_WRITE)
	public Response updateSession(UUID eventId, UUID sessionId, Session session) {
		try {
			repository.updateSession(eventId, sessionId, session);
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
	@RolesAllowed(EVENT_WRITE)
	public Response deleteSession(UUID eventId, UUID sessionId) {
		try {
			repository.deleteSession(eventId, sessionId);
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
	@RolesAllowed(EVENT_READ)
	public Response getSession(UUID eventId, UUID sessionId) {
		try {
			Session session = repository.loadSession(eventId, sessionId);
			return Response.ok(session).build();
		} catch (SQLException e) {
			throw new BadRequestException(e);
		}
	}

}
