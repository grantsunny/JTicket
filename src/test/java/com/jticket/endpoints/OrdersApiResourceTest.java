package com.jticket.endpoints;

import static com.jticket.security.OAuth2Scopes.ORDER_READ_ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.UUID;

import com.jticket.api.model.Order;
import com.jticket.api.model.Payment;
import com.jticket.integration.OrderPluginHelper;
import com.jticket.persist.OrdersRepository;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class OrdersApiResourceTest {

    private OrdersApiResource resource;
    private OrdersRepository repository;
    private OrderPluginHelper plugin;
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() {
        resource = new OrdersApiResource();
        repository = org.mockito.Mockito.mock(OrdersRepository.class);
        plugin = org.mockito.Mockito.mock(OrderPluginHelper.class);
        securityContext = org.mockito.Mockito.mock(SecurityContext.class);

        ReflectionTestUtils.setField(resource, "repository", repository);
        ReflectionTestUtils.setField(resource, "plugin", plugin);
        ReflectionTestUtils.setField(resource, "securityContext", securityContext);
    }

    @Test
    void createOrderUsesAuthenticatedPrincipalAsOrderOwner() throws Exception {
        UriInfo uriInfo = org.mockito.Mockito.mock(UriInfo.class);
        ReflectionTestUtils.setField(resource, "uriInfo", uriInfo);
        when(uriInfo.getRequestUriBuilder()).thenReturn(UriBuilder.fromUri("http://localhost/api/orders"));
        authenticateAs("customer-sub");

        Order order = new Order()
                .userId("spoofed-user")
                .eventId(UUID.randomUUID());
        when(plugin.beforePlaceOrder(eq("customer-sub"), any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        Response response = resource.createOrder(order);

        assertThat(response.getStatus()).isEqualTo(201);
        ArgumentCaptor<Order> savedOrder = ArgumentCaptor.forClass(Order.class);
        verify(repository).saveNewOrder(savedOrder.capture());
        assertThat(savedOrder.getValue().getUserId()).isEqualTo("customer-sub");
    }

    @Test
    void customerReadUsesAuthenticatedPrincipalForOwnershipCheck() throws Exception {
        UUID orderId = UUID.randomUUID();
        authenticateAs("customer-sub");
        when(repository.isUserOrderExist("customer-sub", orderId)).thenReturn(true);
        when(repository.loadOrder(orderId)).thenReturn(new Order().id(orderId).userId("customer-sub"));

        Response response = resource.getOrder(orderId);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(repository).isUserOrderExist("customer-sub", orderId);
    }

    @Test
    void operatorReadAllSkipsOwnerCheck() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(securityContext.isUserInRole(ORDER_READ_ALL)).thenReturn(true);
        when(repository.loadOrder(orderId)).thenReturn(new Order().id(orderId).userId("customer-sub"));

        Response response = resource.getOrder(orderId);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(repository, never()).isUserOrderExist(any(), any());
    }

    @Test
    void paymentDoesNotRequireCustomerOwnership() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment().paidAmount(1234);
        Order order = new Order().id(orderId).userId("customer-sub");
        when(repository.loadOrder(orderId)).thenReturn(order);

        Response response = resource.payOrder(orderId, payment);

        assertThat(response.getStatus()).isEqualTo(202);
        verify(repository, never()).isUserOrderExist(any(), any());
        verify(plugin).beforePayOrder(order, 1234);
        verify(repository).updateOrderPayAmount(orderId, 1234);
    }

    private void authenticateAs(String userName) {
        when(securityContext.getUserPrincipal()).thenReturn(new Principal() {
            @Override
            public String getName() {
                return userName;
            }
        });
    }
}
