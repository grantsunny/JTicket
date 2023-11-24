CREATE SCHEMA TKT;
SET SCHEMA TKT;

-- Create the Venue table
CREATE TABLE TKT.Venues (
                       id VARCHAR(36) PRIMARY KEY,
                       name VARCHAR(255) NOT NULL,
                       metadata CLOB
);

-- Create the Area table
CREATE TABLE TKT.Areas (
                      id VARCHAR(36) PRIMARY KEY,
                      venueId VARCHAR(36) NOT NULL,
                      name VARCHAR(255) NOT NULL,
                      metadata CLOB,
                      FOREIGN KEY (venueId) REFERENCES TKT.Venues(id)
);

-- Create the Seat table
CREATE TABLE TKT.Seats (
                      id VARCHAR(36) PRIMARY KEY,
                      areaId VARCHAR(36) NOT NULL,
                      name VARCHAR(255) NOT NULL,
                      row INT,
                      col INT,
                      available BOOLEAN,
                      metadata CLOB,
                      FOREIGN KEY (areaId) REFERENCES TKT.Areas(id)
);

CREATE TABLE TKT.Events (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    venueId VARCHAR(36),
                    startTime TIMESTAMP,
                    endTime TIMESTAMP,
                    metadata CLOB,
                    FOREIGN KEY (venueId) REFERENCES TKT.Venues(id)
);

CREATE TABLE TKT.Orders (
                    id VARCHAR(36) PRIMARY KEY,
                    eventId VARCHAR(36) NOT NULL,
                    timestamp TIMESTAMP,
                    amountDue INT,
                    paid BOOLEAN,
                    metadata CLOB,
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id)
);

CREATE TABLE TKT.OrderSeats (
                    orderId VARCHAR(36),
                    seatId VARCHAR(36),
                    PRIMARY KEY (orderId, seatId),
                    FOREIGN KEY (orderId) REFERENCES TKT.Orders(id),
                    FOREIGN KEY (seatId) REFERENCES TKT.Seats(id)
);

CREATE VIEW TKT.SeatsBooked AS
SELECT
    TKT.Seats.*,
    TKT.Events.id AS eventId,
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM TKT.OrderSeats
                     INNER JOIN TKT.Orders ON
                    TKT.Orders.id = TKT.OrderSeats.orderId
            WHERE TKT.OrderSeats.seatId = TKT.Seats.Id
              AND TKT.Orders.eventId = TKT.Events.Id
              AND TKT.Orders.paid = TRUE
        )
            THEN TRUE
        ELSE FALSE
        END AS booked
FROM
    TKT.Seats
        INNER JOIN TKT.Areas ON TKT.Seats.areaId = TKT.Areas.id
        LEFT OUTER JOIN TKT.Events ON TKT.Events.venueId = TKT.Areas.venueId
;

CREATE TABLE TKT.Prices (
                    id VARCHAR(36) PRIMARY KEY,
                    eventId VARCHAR(36) NOT NULL,
                    name VARCHAR(255),
                    price DECIMAL(10, 2),
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id)
);

CREATE TABLE TKT.PricesAssignments (
                    id VARCHAR(36) PRIMARY KEY,
                    priceId VARCHAR(36) NOT NULL,
                    seatId VARCHAR(36),
                    areaId VARCHAR(36),
                    venueId VARCHAR(36),
                    FOREIGN KEY (priceId) REFERENCES TKT.Prices(id),
                    FOREIGN KEY (seatId) REFERENCES TKT.Seats(id),
                    FOREIGN KEY (areaId) REFERENCES TKT.Areas(id),
                    FOREIGN KEY (venueId) REFERENCES TKT.Venues(id),
                    UNIQUE(priceId, seatId),
                    UNIQUE(priceId, areaId),
                    UNIQUE(priceId, venueId)
);

CREATE VIEW TKT.SeatPrices AS
SELECT
    TKT.Seats.*,
    TKT.Events.id AS eventId,
    COALESCE(
            seatPrice.price,
            areaPrice.price,
            venuePrice.price
    ) AS price
FROM
    TKT.Seats
        INNER JOIN TKT.Areas ON TKT.Areas.Id = TKT.Seats.areaId
        INNER JOIN TKT.Venues ON TKT.Venues.id = TKT.Areas.venueId
        LEFT OUTER JOIN TKT.Events ON TKT.Events.venueId = TKT.Venues.id

        LEFT JOIN TKT.PricesAssignments paSeat ON TKT.Seats.id = paSeat.seatId
        LEFT JOIN TKT.Prices seatPrice ON paSeat.priceId = seatPrice.Id AND seatPrice.eventId = TKT.Events.id

        LEFT JOIN TKT.PricesAssignments paArea ON TKT.Areas.id = paArea.areaId
        LEFT JOIN TKT.Prices areaPrice ON paArea.priceId = areaPrice.id AND areaPrice.eventId = TKT.Events.id

        LEFT JOIN TKT.PricesAssignments paVenue ON TKT.Venues.id = paVenue.venueId
        LEFT JOIN TKT.Prices venuePrice ON paVenue.priceId = venuePrice.id AND venuePrice.eventId = TKT.Events.id
;
