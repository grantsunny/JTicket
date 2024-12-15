package com.jticket.endpoints;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;

import com.jticket.api.OrdersApi;
import com.jticket.api.model.LinkSeat;
import com.jticket.api.model.Order;
import com.jticket.api.model.Payment;
import com.jticket.api.model.Seat;
import com.jticket.api.model.Ticket;
import com.jticket.integration.OrderPluginHelper;
import com.jticket.persist.OrdersRepository;
import com.jticket.persist.PersistenceException;

import io.jsonwebtoken.Jwts;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

public class OrdersApiResource implements OrdersApi {

    @Context
    private UriInfo uriInfo;

    @Inject
    private OrdersRepository repository;

    @Inject
    private OrderPluginHelper plugin;
    
    @Value("${ticket.jwt.private-key}")
    private String privateKeyPem;
    
    @Value("${ticket.jwt.algorithm:RSA}")
    private String keyAlgorithm;
    
    private PrivateKey privateKey;
    
    private PrivateKey parsePrivateKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
    	
    	if (privateKey != null)
    		return privateKey;
    	
        byte[] privateKeyBytes = Base64.getDecoder().decode(
        	privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "")
        );

        // Generate RSA private key
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        PrivateKey privateKey = KeyFactory.getInstance(keyAlgorithm).generatePrivate(keySpec);
        
        this.privateKey = privateKey;
        return privateKey;
    }
    
    private void verifyOrderAndUser(String userId, UUID orderId) {
        if ((userId == null) || (userId.isEmpty()))
            throw new WebApplicationException("Cannot handle order with empty user information", Response.Status.BAD_REQUEST);

        try {
            if (!repository.isUserOrderExist(userId, orderId))
                throw new WebApplicationException("UserId is not the owner of given order", Response.Status.NOT_FOUND);
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }
    
    /**
     *  JWT (JSON Web Token) used for authentication and access control. The JWT is
        provided as a Base64 URL-encoded string consisting of three parts: header,
        payload, and signature. Here is example payload before encoded as base64:
        
        {
          "sub": "user123456",
          "exp": 1692995200,  // Represents an expiration time in Unix time (e.g., Thu, 24 Sep 2023 00:00:00 GMT)
          "iat": 1692918800,  // Represents the time the JWT was issued (Unix time)
          "event": "evt123",
          "session": "sess456",
          "venue": "ven789",
          "seat": "seat001",
          "area": "area321",
          "row": "12",
          "col": "34",
          "price_name": "VIP"
        }
     * 
     * Unpaid order shall be rejected with 402 payment required (RFC 9110)
     */
    
    @Override
    public Response createCheckInToken(
            UUID orderId, LinkSeat linkSeat, String userIdCookie, String userIdHeader) {
    	
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        verifyOrderAndUser(userId, orderId);
        
        Order order;
		try {
			order = repository.loadOrder(orderId);
		} catch (PersistenceException e) {
			throw new WebApplicationException(e.getMessage(), e);
		}
		
        if (order.getPaidAmount() <= 0)
            throw new WebApplicationException("Cannot get ticket token for unpaid order", Response.Status.PAYMENT_REQUIRED);

        Seat seat = order.getSeats().stream()
        		.filter(new Predicate<> () {
					@Override
					public boolean test(@Valid Seat t) {
						return t.getId().equals(linkSeat.getSeatId());
					}
        			
        		}).findFirst().orElseThrow(new Supplier<WebApplicationException> () {
					@Override
					public WebApplicationException get() {
						return new WebApplicationException("Cannot find specified seat in given order", Response.Status.BAD_REQUEST);
					}
        		});
        
        //construct JWT token accordingly. 
        
        PrivateKey privateKey;
		try {
			privateKey = parsePrivateKey();
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new WebApplicationException(e.getMessage(), e);
		}
        
        String sub = userId;
        UUID eventId = order.getEventId();
        UUID sessionId = order.getSessionId();
        UUID venueId = seat.getVenueId();
        UUID areaId = seat.getAreaId();
        UUID seatId = seat.getId();
        Integer col = seat.getCol();
        Integer row = seat.getRow();
        
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiration = new Date(nowMillis + 5 * 60 * 1000); // The token will be valid for 5 minutes
        
        // Generate the signed JWT token
        String jwtToken = Jwts.builder()
                .subject(sub)                       	// Subject (e.g., user ID)
                .issuer("JTicket")                   	// Issuer
                .issuedAt(now)                          // Issued at timestamp
                .expiration(expiration)                 // Expiration timestamp
                .claim("event", eventId)
                .claim("session", sessionId)
                .claim("venue", venueId)                 
                .claim("area", areaId) 
                .claim("seat", seatId)
                .claim("col", col)
                .claim("row", row)
                .claims(order.getMetadata())
                .claims(seat.getMetadata())
                .signWith(privateKey) 					
                .compact();                             // Serialize to a compact JWT string

        return Response.ok(new Ticket().token(jwtToken)).build();
    }

    @Override
    public Response createOrder(Order order, String userIdCookie, String userIdHeader) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        if (userId != null)
            order.setUserId(userId);
        else
            userId = order.getUserId();

        if (userId == null)
            throw new WebApplicationException("Cannot create order without user information", Response.Status.BAD_REQUEST);

        try {
            order.setId(UUID.randomUUID());
            order = plugin.beforePlaceOrder(userId, order);
            repository.saveNewOrder(order);
            UriBuilder uriBuilder =
                    uriInfo.getRequestUriBuilder().
                            path(String.valueOf(order.getId()));

            return Response.created(uriBuilder.build()).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }



    @Override
    public Response getAllOrders(String userIdCookie, String userIdHeader, Date startTime, Date endTime) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        List<Order> orders;
        try {
            if (startTime != null && endTime != null)
                if (userId != null)
                    orders = repository.loadOrders(userId, startTime, endTime);
                else
                    orders = repository.loadOrders(startTime, endTime);
            else
            if (userId != null)
                orders = repository.loadOrders(userId);
            else
                orders = repository.loadOrders();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }

        if ((orders == null) || (orders.isEmpty()))
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        else
            return Response.ok(orders).build();
    }

    @Override
    public Response getOrder(UUID orderId, String userIdCookie, String userIdHeader) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        verifyOrderAndUser(userId, orderId);
        try {
            Order order = repository.loadOrder(orderId);
            return Response.ok(order).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response payOrder(UUID orderId, Payment payment, String userIdCookie, String userIdHeader) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        verifyOrderAndUser(userId, orderId);
        try {
            plugin.beforePayOrder(userId, orderId.toString(), payment.getPaidAmount());
            repository.updateOrderPayAmount(orderId, payment.getPaidAmount());
            return Response.accepted().build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    public Response cancelOrder(UUID orderId, String userIdCookie, String userIdHeader) {
        String userId = userIdHeader != null ? userIdHeader : userIdCookie;
        verifyOrderAndUser(userId, orderId);
        try {
            plugin.beforeCancelOrder(userId, orderId.toString());
            repository.deleteOrder(orderId);
            return Response.noContent().build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }
}
