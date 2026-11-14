package com.jticket.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jticket.JTicketApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Opt-in HTTP benchmark: -Djticket.performance=true -Djticket.performance.output=.m2/performance.json. */
@SpringBootTest(classes = JTicketApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:derby:memory:jticket-performance;user=TKT;create=true",
                "spring.datasource.driver-class-name=org.apache.derby.iapi.jdbc.AutoloadedDriver",
                "spring.datasource.hikari.maximum-pool-size=10"
        })
@ActiveProfiles({"dev", "test"})
@EnabledIfSystemProperty(named = "jticket.performance", matches = "true")
class PerformanceEvaluationTest {
    private static final int SEATS = 1200;
    private static final int ORDERS = 160;
    private static final int SEATS_PER_ORDER = 2;
    private static final int WARMUP_REQUESTS = 5;
    private static final int LATENCY_SAMPLES = 20;
    private static final int THROUGHPUT_REQUESTS = 40;
    private static final int CLIENTS = 4;
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void evaluateReadWorkloads() throws Exception {
        UUID venue = UUID.randomUUID();
        UUID area = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        UUID price = UUID.randomUUID();
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\">"
                + "<rect width=\"20\" height=\"20\"/>".repeat(10000) + "</svg>";
        jdbc.update("INSERT INTO TKT.Venues(id,name,svg) VALUES (?,?,?)",
                venue.toString(), "Performance venue", svg);
        jdbc.update("INSERT INTO TKT.Areas(id,venueId,name) VALUES (?,?,?)",
                area.toString(), venue.toString(), "Main");
        jdbc.update("INSERT INTO TKT.Events(id,venueId,name) VALUES (?,?,?)",
                event.toString(), venue.toString(), "Performance event");
        jdbc.update("INSERT INTO TKT.Sessions(id,eventId,name,startTime,endTime) "
                + "VALUES (?,?,?,TIMESTAMP('2031-01-01 10:00:00'),TIMESTAMP('2031-01-01 12:00:00'))",
                session.toString(), event.toString(), "Opening");
        jdbc.update("INSERT INTO TKT.Prices(id,eventId,name,price) VALUES (?,?,?,2500)",
                price.toString(), event.toString(), "Standard");
        jdbc.update("INSERT INTO TKT.PricesDistribution(id,priceId,venueId) VALUES (?,?,?)",
                UUID.randomUUID().toString(), price.toString(), venue.toString());

        List<Object[]> orders = new ArrayList<>();
        for (int i = 0; i < ORDERS; i++) {
            orders.add(new Object[]{UUID.randomUUID().toString(), event.toString(),
                    session.toString(), "customer-" + i});
        }
        List<Object[]> seats = new ArrayList<>();
        List<Object[]> bookings = new ArrayList<>();
        for (int i = 0; i < SEATS; i++) {
            String seat = UUID.randomUUID().toString();
            seats.add(new Object[]{seat, area.toString(), i / 40, i % 40,
                    i % 11 != 0, "{\"section\":\"main\"}"});
            if (i < ORDERS * SEATS_PER_ORDER) {
                bookings.add(new Object[]{orders.get(i / SEATS_PER_ORDER)[0],
                        event.toString(), session.toString(), seat});
            }
        }
        jdbc.batchUpdate("INSERT INTO TKT.Seats(id,areaId,row,col,available,metadata) VALUES (?,?,?,?,?,?)", seats);
        jdbc.batchUpdate("INSERT INTO TKT.Orders(id,eventId,sessionId,userId) VALUES (?,?,?,?)", orders);
        jdbc.batchUpdate("INSERT INTO TKT.OrderSeats(orderId,eventId,sessionId,seatId) VALUES (?,?,?,?)", bookings);
        jdbc.update("UPDATE TKT.OrderSeats SET checkedInTimestamp=CURRENT_TIMESTAMP WHERE orderId=?",
                orders.getFirst()[0]);

        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("eventOrders", "/api/events/" + event + "/orders");
        paths.put("sessionStatistics", "/api/events/" + event + "/sessions/" + session + "/statistics");
        paths.put("sessionSeats", "/api/events/" + event + "/sessions/" + session + "/areas/" + area + "/seats");
        paths.put("venueSvg", "/api/venues/" + venue + "/svg");

        var loadedOrders = JSON.readTree(get(paths.get("eventOrders")));
        assertThat(loadedOrders.size()).isEqualTo(ORDERS);
        for (var order : loadedOrders) {
            assertThat(order.path("seats").size()).isEqualTo(SEATS_PER_ORDER);
        }
        var statistics = JSON.readTree(get(paths.get("sessionStatistics")));
        assertThat(statistics.path("totalSeats").asInt()).isEqualTo(SEATS);
        assertThat(statistics.path("orderedSeats").asInt()).isEqualTo(ORDERS * SEATS_PER_ORDER);
        assertThat(statistics.path("checkedInSeats").asInt()).isEqualTo(SEATS_PER_ORDER);
        assertThat(statistics.path("uncheckedInSeats").asInt()).isEqualTo((ORDERS - 1) * SEATS_PER_ORDER);
        assertThat(JSON.readTree(get(paths.get("sessionSeats"))).size()).isEqualTo(SEATS);
        assertThat(get(paths.get("venueSvg"))).isEqualTo(svg);
        Map<String, Object> results = new LinkedHashMap<>();
        for (var entry : paths.entrySet()) {
            results.put(entry.getKey(), measure(entry.getValue()));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("java", System.getProperty("java.version"));
        report.put("database", "embedded Derby, in-memory");
        report.put("fixture", Map.of("seats", SEATS, "orders", ORDERS,
                "seatsPerOrder", SEATS_PER_ORDER, "svgBytes", svg.length()));
        report.put("workloads", results);
        Path output = Path.of(System.getProperty("jticket.performance.output", ".m2/performance.json"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("PERFORMANCE " + JSON.writeValueAsString(report));
    }

    private String get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(60)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private Map<String, Object> measure(String path) throws Exception {
        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            get(path);
        }
        double[] latency = new double[LATENCY_SAMPLES];
        for (int i = 0; i < latency.length; i++) {
            long start = System.nanoTime();
            get(path);
            latency[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(latency);
        long start = System.nanoTime();
        try (var executor = Executors.newFixedThreadPool(CLIENTS)) {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < THROUGHPUT_REQUESTS; i++) {
                calls.add(() -> get(path));
            }
            for (var result : executor.invokeAll(calls)) {
                result.get();
            }
        }
        return Map.of("medianMs", latency[latency.length / 2],
                "p95Ms", latency[(int) Math.ceil(latency.length * 0.95) - 1],
                "throughputRps4Clients", THROUGHPUT_REQUESTS / ((System.nanoTime() - start) / 1e9),
                "warmupRequests", WARMUP_REQUESTS, "latencySamples", LATENCY_SAMPLES,
                "throughputRequests", THROUGHPUT_REQUESTS);
    }
}
