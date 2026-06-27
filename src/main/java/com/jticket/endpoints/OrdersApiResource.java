package com.jticket.endpoints;

import static com.jticket.security.OAuth2Scopes.ORDER_PAY;
import static com.jticket.security.OAuth2Scopes.ORDER_READ;
import static com.jticket.security.OAuth2Scopes.ORDER_READ_ALL;
import static com.jticket.security.OAuth2Scopes.ORDER_WRITE;
import static com.jticket.security.OAuth2Scopes.ORDER_WRITE_ALL;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jticket.api.OrdersApi;
import com.jticket.api.model.LinkSeat;
import com.jticket.api.model.Order;
import com.jticket.api.model.Payment;
import com.jticket.api.model.TicketingSeat;
import com.jticket.api.model.Ticket;
import com.jticket.integration.OrderPluginHelper;
import com.jticket.persist.OrdersRepository;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

public class OrdersApiResource implements OrdersApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrdersApiResource.class);

    @Context
    private UriInfo uriInfo;

    @Context
    private SecurityContext securityContext;

    @Inject
    private OrdersRepository repository;

    @Inject
    private OrderPluginHelper plugin;
    
    @Value("${ticket.jwt.private-key}")
    private String privateKeyPem;
    
    @Value("${ticket.jwt.algorithm:RSA}")
    private String keyAlgorithm;

    @Value("${ticket.accept-underpayment:true}")
    private boolean acceptUnderpayment = true;
    
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

    private String currentUserId() {
        if ((securityContext == null) || (securityContext.getUserPrincipal() == null))
            throw new WebApplicationException("Cannot handle order without authenticated user", Response.Status.UNAUTHORIZED);
        return securityContext.getUserPrincipal().getName();
    }

    private String currentUserIdOrNull() {
        if ((securityContext == null) || (securityContext.getUserPrincipal() == null))
            return null;
        return securityContext.getUserPrincipal().getName();
    }

    private boolean hasRole(String role) {
        return (securityContext != null) && securityContext.isUserInRole(role);
    }

    private boolean canReadAllOrders() {
        return hasRole(ORDER_READ_ALL);
    }

    private boolean canWriteAllOrders() {
        return hasRole(ORDER_WRITE_ALL);
    }

    private Order loadOrder(UUID orderId) throws SQLException {
        Order order = repository.loadOrder(orderId);
        if (order == null)
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        return order;
    }

    private Integer calculateSeatTotalAmount(Order order) {
        if (order.getSeats() == null)
            return null;

        int total = 0;
        for (TicketingSeat seat : order.getSeats()) {
            if (seat.getPrice() == null)
                return null;
            total += seat.getPrice();
        }
        return total;
    }

    private void validatePaymentAmount(Order order, Payment payment) {
        Integer seatTotalAmount = calculateSeatTotalAmount(order);
        if (seatTotalAmount == null)
            return;

        if (seatTotalAmount > payment.getPaymentAmount()) {
            String message = "Payment amount is less than order seat total: orderId=%s, paymentAmount=%d, seatTotalAmount=%d"
                    .formatted(order.getId(), payment.getPaymentAmount(), seatTotalAmount);
            if (!acceptUnderpayment)
                throw new WebApplicationException(message, Response.Status.NOT_ACCEPTABLE);
            LOGGER.warn(message);
        } else if (payment.getPaymentAmount() > seatTotalAmount) {
            LOGGER.warn(
                    "Payment amount is greater than order seat total: orderId={}, paymentAmount={}, seatTotalAmount={}",
                    order.getId(), payment.getPaymentAmount(), seatTotalAmount);
        }
    }

    private boolean isSamePayment(Order order, Payment payment) {
        return Objects.equals(order.getPaymentChannel(), payment.getPaymentChannel()) &&
                Objects.equals(order.getPaymentTransactionId(), payment.getPaymentTransactionId()) &&
                Objects.equals(order.getPaymentAmount(), payment.getPaymentAmount());
    }

    private Response existingPaymentResponse(Order order, Payment payment) {
        if (isSamePayment(order, payment)) {
            LOGGER.warn(
                    "Skipping payment plugin for idempotent payment retry: orderId={}, paymentChannel={}, paymentTransactionId={}",
                    order.getId(), payment.getPaymentChannel(), payment.getPaymentTransactionId());
            return Response.accepted().build();
        }

        throw new WebApplicationException(
                "Payment request conflicts with existing order payment", Response.Status.CONFLICT);
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
    @RolesAllowed({ORDER_READ, ORDER_READ_ALL})
    public Response createCheckInToken(UUID orderId, LinkSeat linkSeat) {
    	
        if (!canReadAllOrders())
            verifyOrderAndUser(currentUserId(), orderId);
        
        Order order;
		try {
			order = loadOrder(orderId);
		} catch (SQLException e) {
			throw new WebApplicationException(e.getMessage(), e);
		}
		
        if (order.getPaymentAmount() == null || order.getPaymentAmount() <= 0)
            throw new WebApplicationException("Cannot get ticket token for unpaid order", Response.Status.PAYMENT_REQUIRED);

	        TicketingSeat seat = order.getSeats().stream()
                    .filter(t -> t.getId().equals(linkSeat.getSeatId()))
                    .findFirst()
                    .orElseThrow(() -> new WebApplicationException(
                            "Cannot find specified seat in given order", Response.Status.BAD_REQUEST));
        
        //construct JWT token accordingly. 
        
        PrivateKey privateKey;
		try {
			privateKey = parsePrivateKey();
		} catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new WebApplicationException(e.getMessage(), e);
		}
        
        String sub = order.getUserId();
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
        JwtBuilder jwtBuilder = Jwts.builder();
        if (order.getMetadata() != null)
            jwtBuilder.claims(order.getMetadata());
        if (seat.getMetadata() != null)
            jwtBuilder.claims(seat.getMetadata());

        String jwtToken = jwtBuilder
                .subject(sub)                       	// Subject (e.g., user ID)
                .issuer("JTicket")                   	// Issuer
                .issuedAt(now)                          // Issued at timestamp
                .expiration(expiration)                 // Expiration timestamp
                .claim("event", eventId.toString())
                .claim("session", sessionId.toString())
                .claim("venue", venueId.toString())
                .claim("area", areaId.toString())
                .claim("seat", seatId.toString())
                .claim("col", col)
                .claim("row", row)
                .signWith(privateKey)
                .compact();                             // Serialize to a compact JWT string

        return Response.ok(new Ticket().token(jwtToken)).build();
    }

    @Override
    @RolesAllowed(ORDER_WRITE)
    public Response createOrder(Order order) {
        String userId = currentUserIdOrNull();
        if (userId == null)
            userId = order.getUserId();

        if (userId == null)
            throw new WebApplicationException("Cannot create order without user information", Response.Status.BAD_REQUEST);

        try {
            order.setUserId(userId);
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
    @RolesAllowed({ORDER_READ, ORDER_READ_ALL})
    public Response getAllOrders(Date startTime, Date endTime) {
        String userId = canReadAllOrders() ? null : currentUserIdOrNull();
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
    @RolesAllowed({ORDER_READ, ORDER_READ_ALL})
    public Response getOrder(UUID orderId) {
        if (!canReadAllOrders())
            verifyOrderAndUser(currentUserId(), orderId);
        try {
            Order order = loadOrder(orderId);
            return Response.ok(order).build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    @RolesAllowed(ORDER_PAY)
    public synchronized Response payOrder(UUID orderId, Payment payment) {
        if (payment == null ||
                payment.getPaymentChannel() == null || payment.getPaymentChannel().isBlank() ||
                payment.getPaymentTransactionId() == null || payment.getPaymentTransactionId().isBlank() ||
                payment.getPaymentAmount() == null || payment.getPaymentAmount() <= 0)
            throw new WebApplicationException("Payment channel, transaction id, and positive amount are required", Response.Status.BAD_REQUEST);

        try {
            Order order = loadOrder(orderId);
            validatePaymentAmount(order, payment);
            if (order.getPaymentTransactionId() != null)
                return existingPaymentResponse(order, payment);

            plugin.beforePayOrder(order, payment.getPaymentAmount());

            OrdersRepository.PaymentResult result = repository.updateOrderPayment(
                    orderId,
                    payment.getPaymentChannel(),
                    payment.getPaymentTransactionId(),
                    payment.getPaymentAmount());

            return switch (result) {
                case SUCCESS -> Response.accepted().build();
                case IDEMPOTENT_RETRY -> {
                    LOGGER.warn(
                            "Skipping payment plugin for idempotent payment retry: orderId={}, paymentChannel={}, paymentTransactionId={}",
                            orderId, payment.getPaymentChannel(), payment.getPaymentTransactionId());
                    yield Response.accepted().build();
                }
                case CONFLICT -> throw new WebApplicationException(
                        "Payment request conflicts with existing order payment", Response.Status.CONFLICT);
                case ORDER_NOT_FOUND -> throw new WebApplicationException(Response.Status.NOT_FOUND);
            };
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }

    @Override
    @RolesAllowed({ORDER_WRITE, ORDER_WRITE_ALL})
    public Response cancelOrder(UUID orderId) {
        if (!canWriteAllOrders())
            verifyOrderAndUser(currentUserId(), orderId);
        try {
            Order order = loadOrder(orderId);
            plugin.beforeCancelOrder(order);
            repository.deleteOrder(orderId);
            return Response.noContent().build();
        } catch (SQLException e) {
            throw new BadRequestException(e);
        }
    }
}
