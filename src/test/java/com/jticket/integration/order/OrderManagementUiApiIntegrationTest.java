package com.jticket.integration.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jticket.JTicketApplication;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(
        classes = JTicketApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:derby:memory:jticket-order-management-ui-api-integration;create=true",
                "spring.datasource.driver-class-name=org.apache.derby.jdbc.EmbeddedDriver",
                "ticket.accept-underpayment=false"
        })
@ActiveProfiles("dev")
class OrderManagementUiApiIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void orderManagementApiFlowReflectsCreatePayAndCancelInOrderUiSurfaces() throws Exception {
        UUID venueId = uploadVenueTemplate();
        UUID eventId = createEvent(venueId);
        UUID sessionId = createSession(eventId);
        UUID areaId = getOnlyId("/api/events/%s/areas".formatted(eventId));
        List<JsonNode> seats = getArray("/api/events/%s/areas/%s/seats".formatted(eventId, areaId));

        UUID standardPriceId = createPrice(eventId, "standard", 2_500);
        UUID vipPriceId = createPrice(eventId, "vip", 10_000);
        assertStatus(
                patch("/api/events/%s/areas/%s/pricing".formatted(eventId, areaId), Map.of("priceId", standardPriceId)),
                HttpStatus.ACCEPTED);

        UUID standardSeatId = seatIdAt(seats, 0, 0);
        UUID vipSeatId = seatIdAt(seats, 0, 1);
        UUID cancellableSeatId = seatIdAt(seats, 1, 0);
        assertStatus(
                patch("/api/events/%s/seats/%s/pricing".formatted(eventId, vipSeatId), Map.of("priceId", vipPriceId)),
                HttpStatus.ACCEPTED);

        UUID paidOrderId = createOrder(eventId, sessionId, "order-api-customer", standardSeatId, vipSeatId);
        ResponseEntity<String> duplicateSeatOrder = post("/api/orders", Map.of(
                "userId", "order-api-customer",
                "eventId", eventId,
                "sessionId", sessionId,
                "seats", List.of(Map.of("id", standardSeatId))));
        assertThat(duplicateSeatOrder.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertStatus(payOrder(paidOrderId, "card", "txn-order-flow-1", 12_500), HttpStatus.ACCEPTED);
        assertStatus(payOrder(paidOrderId, "card", "txn-order-flow-1", 12_500), HttpStatus.ACCEPTED);
        assertStatus(payOrder(paidOrderId, "card", "txn-order-flow-2", 12_500), HttpStatus.CONFLICT);
        assertStatus(payOrder(paidOrderId, "card", "txn-order-flow-underpay", 1_000), HttpStatus.CONFLICT);

        UUID canceledOrderId = createOrder(eventId, sessionId, "cancel-api-customer", cancellableSeatId);
        assertStatus(delete("/api/orders/%s".formatted(canceledOrderId)), HttpStatus.NO_CONTENT);
        assertStatus(delete("/api/orders/%s".formatted(paidOrderId)), HttpStatus.NOT_FOUND);

        List<JsonNode> eventOrders = getArray("/api/events/%s/orders".formatted(eventId));
        assertThat(eventOrders).hasSize(1);
        JsonNode paidOrder = eventOrders.get(0);
        assertThat(paidOrder.path("id").asText()).isEqualTo(paidOrderId.toString());
        assertThat(paidOrder.path("sessionId").asText()).isEqualTo(sessionId.toString());
        assertThat(paymentTextLikeOrderUi(paidOrder)).isEqualTo("card 125.00");
        assertThat(paidAmountLikeOrderUi(eventOrders)).isEqualTo(12_500);
        assertThat(sessionOrdersLikeOrderUi(eventOrders, sessionId)).hasSize(1);
        assertThat(paidOrder.path("seats")).hasSize(2);

        JsonNode orderDetails = getObject("/api/orders/%s".formatted(paidOrderId));
        assertThat(orderDetails.path("paymentChannel").asText()).isEqualTo("card");
        assertThat(orderDetails.path("paymentTransactionId").asText()).isEqualTo("txn-order-flow-1");
        assertThat(orderDetails.path("paymentAmount").asInt()).isEqualTo(12_500);

        JsonNode eventStatistics = getObject("/api/events/%s/statistics".formatted(eventId));
        assertThat(eventStatistics.path("totalSeats").asInt()).isEqualTo(4);
        assertThat(eventStatistics.path("orderedSeats").asInt()).isEqualTo(2);
        assertThat(eventStatistics.path("checkedInSeats").asInt()).isZero();

        JsonNode sessionStatistics = getObject("/api/events/%s/sessions/%s/statistics".formatted(eventId, sessionId));
        assertThat(sessionStatistics.path("totalSeats").asInt()).isEqualTo(4);
        assertThat(sessionStatistics.path("orderedSeats").asInt()).isEqualTo(2);
        assertThat(sessionStatistics.path("checkedInSeats").asInt()).isZero();

        List<JsonNode> sessionSeats = getArray("/api/events/%s/sessions/%s/areas/%s/seats"
                .formatted(eventId, sessionId, areaId));
        JsonNode standardSeat = seatById(sessionSeats, standardSeatId);
        JsonNode vipSeat = seatById(sessionSeats, vipSeatId);
        JsonNode canceledSeat = seatById(sessionSeats, cancellableSeatId);

        assertSeatLooksBookedToOrderUi(standardSeat, paidOrderId, "order-api-customer", "standard", 2_500);
        assertSeatLooksBookedToOrderUi(vipSeat, paidOrderId, "order-api-customer", "vip", 10_000);
        assertThat(canceledSeat.path("status").asText()).isEqualTo("open");
        assertThat(canceledSeat.hasNonNull("orderId")).isFalse();
        assertThat(seatTooltipPriceLikeOrderUi(vipSeat)).isEqualTo("Price: vip 100.00");

        OrderAreaSummary areaSummary = summarizeAreaLikeOrderUi(sessionSeats);
        assertThat(areaSummary.totalSeats()).isEqualTo(4);
        assertThat(areaSummary.bookedSeats()).isEqualTo(2);
        assertThat(areaSummary.checkedInSeats()).isZero();
    }

    private UUID uploadVenueTemplate() throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(testVenueWorkbook()) {
            @Override
            public String getFilename() {
                return "order-management-ui-api-template.xlsx";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/template",
                new HttpEntity<>(body, headers),
                String.class);

        assertStatus(response, HttpStatus.CREATED);
        return lastUuid(response.getHeaders().getLocation());
    }

    private byte[] testVenueWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row venueRow = workbook.createSheet("venue").createRow(0);
            venueRow.createCell(0).setCellValue("name");
            venueRow.createCell(1).setCellValue("Order Management Integration Venue");

            Row areaRow = workbook.createSheet("area1").createRow(0);
            areaRow.createCell(0).setCellValue("name");
            areaRow.createCell(1).setCellValue("Main Area");

            var layoutSheet = workbook.createSheet("venue-layout");
            var style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var layoutCell = layoutSheet.createRow(0).createCell(0);
            layoutCell.setCellValue("1");
            layoutCell.setCellStyle(style);

            var seatsSheet = workbook.createSheet("area1-seats");
            Row firstRow = seatsSheet.createRow(0);
            firstRow.createCell(0).setCellValue("Y");
            firstRow.createCell(1).setCellValue("Y");
            Row secondRow = seatsSheet.createRow(1);
            secondRow.createCell(0).setCellValue("Y");
            secondRow.createCell(1).setCellValue("N");

            workbook.write(output);
            return output.toByteArray();
        }
    }

    private UUID createEvent(UUID venueId) {
        ResponseEntity<String> response = post("/api/events", Map.of(
                "name", "Order Management API Flow",
                "venueId", venueId));

        assertStatus(response, HttpStatus.CREATED);
        return lastUuid(response.getHeaders().getLocation());
    }

    private UUID createSession(UUID eventId) {
        ResponseEntity<String> response = post("/api/events/%s/sessions".formatted(eventId), Map.of(
                "eventId", eventId,
                "name", "Opening",
                "startTime", "2031-01-01 10:00:00",
                "endTime", "2031-01-01 12:00:00"));

        assertStatus(response, HttpStatus.CREATED);
        return lastUuid(response.getHeaders().getLocation());
    }

    private UUID createPrice(UUID eventId, String name, int price) {
        ResponseEntity<String> response = post("/api/events/%s/prices".formatted(eventId), Map.of(
                "eventId", eventId,
                "name", name,
                "price", price));

        assertStatus(response, HttpStatus.CREATED);
        return lastUuid(response.getHeaders().getLocation());
    }

    private UUID createOrder(UUID eventId, UUID sessionId, String userId, UUID... seatIds) {
        List<Map<String, UUID>> seats = new ArrayList<>();
        for (UUID seatId : seatIds) {
            seats.add(Map.of("id", seatId));
        }

        ResponseEntity<String> response = post("/api/orders", Map.of(
                "userId", userId,
                "eventId", eventId,
                "sessionId", sessionId,
                "seats", seats));

        assertStatus(response, HttpStatus.CREATED);
        return lastUuid(response.getHeaders().getLocation());
    }

    private ResponseEntity<String> post(String path, Object body) {
        return restTemplate.postForEntity(path, jsonEntity(body), String.class);
    }

    private ResponseEntity<String> patch(String path, Object body) {
        return restTemplate.exchange(path, HttpMethod.PATCH, jsonEntity(body), String.class);
    }

    private ResponseEntity<String> delete(String path) {
        return restTemplate.exchange(path, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
    }

    private ResponseEntity<String> payOrder(UUID orderId, String channel, String transactionId, int amount) {
        return patch("/api/orders/%s".formatted(orderId), Map.of(
                "paymentChannel", channel,
                "paymentTransactionId", transactionId,
                "paymentAmount", amount));
    }

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private List<JsonNode> getArray(String path) throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        assertStatus(response, HttpStatus.OK);

        List<JsonNode> nodes = new ArrayList<>();
        for (JsonNode node : OBJECT_MAPPER.readTree(response.getBody())) {
            nodes.add(node);
        }
        return nodes;
    }

    private JsonNode getObject(String path) throws IOException {
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        assertStatus(response, HttpStatus.OK);
        return OBJECT_MAPPER.readTree(response.getBody());
    }

    private UUID getOnlyId(String path) throws IOException {
        List<JsonNode> nodes = getArray(path);
        assertThat(nodes).hasSize(1);
        return UUID.fromString(nodes.get(0).path("id").asText());
    }

    private UUID seatIdAt(List<JsonNode> seats, int row, int col) {
        return UUID.fromString(seatAt(seats, row, col).path("id").asText());
    }

    private JsonNode seatAt(List<JsonNode> seats, int row, int col) {
        return seats.stream()
                .filter(seat -> seat.path("row").asInt() == row && seat.path("col").asInt() == col)
                .findFirst()
                .orElseThrow();
    }

    private JsonNode seatById(List<JsonNode> seats, UUID seatId) {
        return seats.stream()
                .filter(seat -> seatId.toString().equals(seat.path("id").asText()))
                .findFirst()
                .orElseThrow();
    }

    private void assertSeatLooksBookedToOrderUi(
            JsonNode seat,
            UUID orderId,
            String userId,
            String priceName,
            int price) {
        assertThat(seat.path("status").asText()).isEqualTo("booked");
        assertThat(seat.path("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(seat.path("userId").asText()).isEqualTo(userId);
        assertThat(seat.path("priceName").asText()).isEqualTo(priceName);
        assertThat(seat.path("price").asInt()).isEqualTo(price);
    }

    private int paidAmountLikeOrderUi(List<JsonNode> orders) {
        int total = 0;
        for (JsonNode order : orders) {
            if (order.hasNonNull("paymentAmount")) {
                total += order.path("paymentAmount").asInt();
            }
        }
        return total;
    }

    private List<JsonNode> sessionOrdersLikeOrderUi(List<JsonNode> orders, UUID sessionId) {
        return orders.stream()
                .filter(order -> sessionId.toString().equals(order.path("sessionId").asText()))
                .toList();
    }

    private String paymentTextLikeOrderUi(JsonNode order) {
        if (!order.hasNonNull("paymentTransactionId")) {
            return "Unpaid";
        }

        String amount = order.hasNonNull("paymentAmount")
                ? moneyText(order.path("paymentAmount").asInt())
                : "";
        String channel = order.path("paymentChannel").asText("Paid");
        return amount.isBlank() ? channel : "%s %s".formatted(channel, amount);
    }

    private String seatTooltipPriceLikeOrderUi(JsonNode seat) {
        if (!seat.hasNonNull("priceName") && !seat.hasNonNull("price")) {
            return "";
        }
        String name = seat.path("priceName").asText("");
        String price = seat.hasNonNull("price") ? " " + moneyText(seat.path("price").asInt()) : "";
        return "Price: %s%s".formatted(name, price).trim();
    }

    private OrderAreaSummary summarizeAreaLikeOrderUi(List<JsonNode> seats) {
        int bookedSeats = 0;
        int checkedInSeats = 0;
        for (JsonNode seat : seats) {
            String status = seat.path("status").asText();
            if ("booked".equals(status) || "checkedIn".equals(status)) {
                bookedSeats++;
            }
            if ("checkedIn".equals(status)) {
                checkedInSeats++;
            }
        }
        return new OrderAreaSummary(seats.size(), bookedSeats, checkedInSeats);
    }

    private String moneyText(int cents) {
        return "%.2f".formatted(cents / 100.0);
    }

    private UUID lastUuid(URI location) {
        assertThat(location).isNotNull();
        String path = location.getPath();
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    private void assertStatus(ResponseEntity<?> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
    }

    private record OrderAreaSummary(int totalSeats, int bookedSeats, int checkedInSeats) {
    }
}
