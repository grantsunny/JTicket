package com.jticket.integration.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
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
                "spring.datasource.url=jdbc:derby:memory:jticket-pricing-ui-api-integration;create=true",
                "spring.datasource.driver-class-name=org.apache.derby.jdbc.EmbeddedDriver"
        })
@AutoConfigureTestRestTemplate
@ActiveProfiles("dev")
class PricingUiApiIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void pricingPageApiFlowReportsSoldSeatsAndBlocksRepricingOrderedSeats() throws Exception {
        UUID venueId = uploadVenueTemplate();
        UUID eventId = createEvent(venueId);
        UUID sessionId = createSession(eventId);
        UUID areaId = getOnlyId("/api/events/%s/areas".formatted(eventId));
        List<JsonNode> seats = getArray("/api/events/%s/areas/%s/seats".formatted(eventId, areaId));
        assertThat(seats).hasSize(4);

        UUID standardPriceId = createPrice(eventId, "standard", 1_000);
        UUID vipPriceId = createPrice(eventId, "vip", 10_000);
        UUID unusedPriceId = createPrice(eventId, "unused", 2_000);

        assertStatus(delete("/api/events/%s/prices/%s".formatted(eventId, unusedPriceId)), HttpStatus.NO_CONTENT);
        assertThat(getArray("/api/events/%s/prices".formatted(eventId)))
                .noneMatch(price -> unusedPriceId.toString().equals(price.path("id").asText()));

        JsonNode emptyAreaPricing = getObject("/api/events/%s/areas/%s/pricing".formatted(eventId, areaId));
        assertThat(emptyAreaPricing.path("effectivePricing").path("source").asText()).isEqualTo("none");
        assertThat(emptyAreaPricing.hasNonNull("priceId")).isFalse();
        assertThat(emptyAreaPricing.hasNonNull("effectivePricing")).isTrue();

        assertStatus(
                patch("/api/events/%s/areas/%s/pricing".formatted(eventId, areaId), Map.of("priceId", standardPriceId)),
                HttpStatus.ACCEPTED);

        JsonNode assignedAreaPricing = getObject("/api/events/%s/areas/%s/pricing".formatted(eventId, areaId));
        assertThat(assignedAreaPricing.path("priceId").asText()).isEqualTo(standardPriceId.toString());
        assertThat(assignedAreaPricing.path("effectivePricing").path("priceId").asText()).isEqualTo(standardPriceId.toString());
        assertThat(assignedAreaPricing.path("effectivePricing").path("name").asText()).isEqualTo("standard");
        assertThat(assignedAreaPricing.path("effectivePricing").path("price").asInt()).isEqualTo(1_000);
        assertThat(assignedAreaPricing.path("effectivePricing").path("source").asText()).isEqualTo("area");

        assertThat(delete("/api/events/%s/prices/%s".formatted(eventId, standardPriceId)).getStatusCode())
                .isIn(HttpStatus.NOT_MODIFIED, HttpStatus.CONFLICT);

        assertStatus(
                patch("/api/events/%s/areas/%s/pricing".formatted(eventId, areaId), nullablePriceBody()),
                HttpStatus.ACCEPTED);
        List<JsonNode> clearedAreaSeats = getArray("/api/events/%s/areas/%s/seats".formatted(eventId, areaId));
        assertThat(seatAt(clearedAreaSeats, 0, 0).hasNonNull("price")).isFalse();

        assertStatus(
                patch("/api/events/%s/areas/%s/pricing".formatted(eventId, areaId), Map.of("priceId", standardPriceId)),
                HttpStatus.ACCEPTED);

        UUID soldSeatId = seatIdAt(seats, 0, 0);
        UUID upgradedSeatId = seatIdAt(seats, 0, 1);

        assertStatus(
                patch("/api/events/%s/seats/%s/pricing".formatted(eventId, upgradedSeatId), Map.of("priceId", vipPriceId)),
                HttpStatus.ACCEPTED);

        JsonNode assignedSeatPricing = getObject("/api/events/%s/seats/%s/pricing".formatted(eventId, upgradedSeatId));
        assertThat(assignedSeatPricing.path("priceId").asText()).isEqualTo(vipPriceId.toString());
        assertThat(assignedSeatPricing.path("effectivePricing").path("priceId").asText()).isEqualTo(vipPriceId.toString());
        assertThat(assignedSeatPricing.path("effectivePricing").path("name").asText()).isEqualTo("vip");
        assertThat(assignedSeatPricing.path("effectivePricing").path("price").asInt()).isEqualTo(10_000);
        assertThat(assignedSeatPricing.path("effectivePricing").path("source").asText()).isEqualTo("seat");

        assertStatus(
                patch("/api/events/%s/seats/%s/pricing".formatted(eventId, upgradedSeatId), nullablePriceBody()),
                HttpStatus.ACCEPTED);
        JsonNode clearedSeat = getObject("/api/events/%s/seats/%s".formatted(eventId, upgradedSeatId));
        assertThat(clearedSeat.path("price").asInt()).isEqualTo(1_000);
        assertThat(clearedSeat.path("priceName").asText()).isEqualTo("standard");

        JsonNode inheritedSeatPricing = getObject("/api/events/%s/seats/%s/pricing".formatted(eventId, upgradedSeatId));
        assertThat(inheritedSeatPricing.hasNonNull("priceId")).isFalse();
        assertThat(inheritedSeatPricing.path("effectivePricing").path("priceId").asText()).isEqualTo(standardPriceId.toString());
        assertThat(inheritedSeatPricing.path("effectivePricing").path("name").asText()).isEqualTo("standard");
        assertThat(inheritedSeatPricing.path("effectivePricing").path("price").asInt()).isEqualTo(1_000);
        assertThat(inheritedSeatPricing.path("effectivePricing").path("source").asText()).isEqualTo("area");

        assertStatus(
                patch("/api/events/%s/seats/%s/pricing".formatted(eventId, upgradedSeatId), Map.of("priceId", vipPriceId)),
                HttpStatus.ACCEPTED);

        createOrder(eventId, sessionId, soldSeatId);

        List<JsonNode> pricingSeats = getArray("/api/events/%s/areas/%s/seats".formatted(eventId, areaId));
        JsonNode soldSeat = seatById(pricingSeats, soldSeatId);
        JsonNode vipSeat = seatById(pricingSeats, upgradedSeatId);
        JsonNode unavailableSeat = seatAt(pricingSeats, 1, 1);

        assertThat(soldSeat.path("sold").asBoolean()).isTrue();
        assertThat(soldSeat.hasNonNull("orderId")).isFalse();
        assertThat(soldSeat.path("price").asInt()).isEqualTo(1_000);
        assertThat(soldSeat.path("priceName").asText()).isEqualTo("standard");

        assertThat(vipSeat.path("sold").asBoolean()).isFalse();
        assertThat(vipSeat.path("price").asInt()).isEqualTo(10_000);
        assertThat(vipSeat.path("priceName").asText()).isEqualTo("vip");

        assertThat(unavailableSeat.path("available").asBoolean()).isFalse();
        assertThat(unavailableSeat.path("metadata").path("ticketingAvailabilityReason").asText())
                .isEqualTo("physicalUnavailable");

        PricingSummary summary = summarizeLikePricingUi(pricingSeats);
        assertThat(summary.totalSeats()).isEqualTo(4);
        assertThat(summary.pricedSeats()).isEqualTo(4);
        assertThat(summary.unpricedSeats()).isZero();
        assertThat(summary.soldSeats()).isEqualTo(1);
        assertThat(summary.totalSellablePrice()).isEqualTo(11_000);
        assertThat(summary.standardSeats()).isEqualTo(3);
        assertThat(summary.vipSeats()).isEqualTo(1);

        ResponseEntity<String> soldSeatReprice = patch(
                "/api/events/%s/seats/%s/pricing".formatted(eventId, soldSeatId),
                Map.of("priceId", vipPriceId));
        assertThat(soldSeatReprice.getStatusCode()).isIn(HttpStatus.NOT_MODIFIED, HttpStatus.CONFLICT);

        ResponseEntity<String> areaReprice = patch(
                "/api/events/%s/areas/%s/pricing".formatted(eventId, areaId),
                Map.of("priceId", vipPriceId));
        assertThat(areaReprice.getStatusCode()).isIn(HttpStatus.NOT_MODIFIED, HttpStatus.CONFLICT);

        List<JsonNode> unchangedSeats = getArray("/api/events/%s/areas/%s/seats".formatted(eventId, areaId));
        assertThat(seatById(unchangedSeats, soldSeatId).path("priceName").asText()).isEqualTo("standard");
        assertThat(seatById(unchangedSeats, upgradedSeatId).path("priceName").asText()).isEqualTo("vip");

        List<JsonNode> sessionSeats = getArray("/api/events/%s/sessions/%s/areas/%s/seats"
                .formatted(eventId, sessionId, areaId));
        JsonNode soldSessionSeat = seatById(sessionSeats, soldSeatId);
        assertThat(soldSessionSeat.path("status").asText()).isEqualTo("booked");
        assertThat(soldSessionSeat.hasNonNull("orderId")).isTrue();
    }

    private UUID uploadVenueTemplate() throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(testVenueWorkbook()) {
            @Override
            public String getFilename() {
                return "pricing-ui-api-template.xlsx";
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
            venueRow.createCell(1).setCellValue("Pricing Integration Venue");

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
                "name", "Pricing API Flow",
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

    private void createOrder(UUID eventId, UUID sessionId, UUID seatId) {
        ResponseEntity<String> response = post("/api/orders", Map.of(
                "userId", "pricing-api-customer",
                "eventId", eventId,
                "sessionId", sessionId,
                "seats", List.of(Map.of("id", seatId))));

        assertStatus(response, HttpStatus.CREATED);
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

    private Map<String, Object> nullablePriceBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("priceId", null);
        return body;
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

    private PricingSummary summarizeLikePricingUi(List<JsonNode> seats) {
        int pricedSeats = 0;
        int unpricedSeats = 0;
        int soldSeats = 0;
        int totalSellablePrice = 0;
        int standardSeats = 0;
        int vipSeats = 0;

        for (JsonNode seat : seats) {
            boolean hasPrice = seat.hasNonNull("price");
            boolean sold = seat.path("sold").asBoolean();
            if (hasPrice) {
                pricedSeats++;
                if ("standard".equals(seat.path("priceName").asText())) {
                    standardSeats++;
                }
                if ("vip".equals(seat.path("priceName").asText())) {
                    vipSeats++;
                }
            } else {
                unpricedSeats++;
            }
            if (sold) {
                soldSeats++;
            }
            if (seat.path("available").asBoolean() && !sold && hasPrice) {
                totalSellablePrice += seat.path("price").asInt();
            }
        }

        return new PricingSummary(
                seats.size(),
                pricedSeats,
                unpricedSeats,
                soldSeats,
                totalSellablePrice,
                standardSeats,
                vipSeats);
    }

    private UUID lastUuid(URI location) {
        assertThat(location).isNotNull();
        String path = location.getPath();
        return UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
    }

    private void assertStatus(ResponseEntity<?> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
    }

    private record PricingSummary(
            int totalSeats,
            int pricedSeats,
            int unpricedSeats,
            int soldSeats,
            int totalSellablePrice,
            int standardSeats,
            int vipSeats) {
    }
}
