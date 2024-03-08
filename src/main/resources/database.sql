CREATE SCHEMA TKT;
SET SCHEMA TKT;

-- Create the Venue table
CREATE TABLE TKT.Venues (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    metadata CLOB,
                    svg CLOB,
                    UNIQUE (name)
);

-- Create the Area table
CREATE TABLE TKT.Areas (
                    id VARCHAR(36) PRIMARY KEY,
                    venueId VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    metadata CLOB,
                    FOREIGN KEY (venueId) REFERENCES TKT.Venues(id),
                    UNIQUE (venueId, name)
);

-- Create the Seat table
CREATE TABLE TKT.Seats (
                    id VARCHAR(36) PRIMARY KEY,
                    areaId VARCHAR(36) NOT NULL,
                    row INT,
                    col INT,
                    available BOOLEAN,
                    metadata CLOB,
                    FOREIGN KEY (areaId) REFERENCES TKT.Areas(id),
                    UNIQUE (areaId, row, col)
);

CREATE TABLE TKT.Events (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    venueId VARCHAR(36),
                    startTime TIMESTAMP NOT NULL,
                    endTime TIMESTAMP NOT NULL,
                    metadata CLOB,
                    FOREIGN KEY (venueId) REFERENCES TKT.Venues(id),
                    UNIQUE(name)
);

CREATE PROCEDURE TKT.RaiseException(IN error VARCHAR(100))
    LANGUAGE JAVA
    PARAMETER STYLE JAVA
    NO SQL
    EXTERNAL NAME 'com.stonematrix.ticket.persist.SpExceptionRaiser.error'
;

CREATE TRIGGER TKT.PreventEventTimeOverLap
    NO CASCADE BEFORE INSERT ON TKT.Events
REFERENCING NEW ROW AS newRow
FOR EACH ROW MODE DB2SQL
WHEN (EXISTS (
        SELECT 1 FROM TKT.Events
        WHERE venueId = newRow.venueId
        AND NOT (startTime >= newRow.endTime OR endTime <= newRow.startTime)))
CALL RaiseException('Event time overlapping encountered')
;

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

CREATE TABLE TKT.Prices (
                    id VARCHAR(36) PRIMARY KEY,
                    eventId VARCHAR(36) NOT NULL,
                    name VARCHAR(255),
                    price DECIMAL(10, 2),
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id),
                    UNIQUE(eventId, name)
);

CREATE TABLE TKT.PricesDistribution (
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

CREATE VIEW TKT.SeatDetails AS
    SELECT TKT.Seats.id, areaId, TKT.Areas.venueId, row, col, available, TKT.Seats.metadata
    FROM TKT.Seats
    INNER JOIN TKT.Areas ON TKT.Areas.id = TKT.Seats.areaId
;

CREATE VIEW TKT.SKU AS
SELECT
    T.*,
    ROW, COL, AVAILABLE, METADATA,
    PRICES.NAME AS priceName, PRICES.PRICE AS price,
    CASE
    WHEN EXISTS (
        SELECT *
        FROM TKT.OrderSeats
                 INNER JOIN TKT.Orders ON
            TKT.Orders.id = TKT.OrderSeats.orderId
        WHERE TKT.OrderSeats.seatId = T.seatId
          AND TKT.Orders.eventId = T.eventId
          AND TKT.Orders.paid = TRUE)
    THEN TRUE
    ELSE FALSE
    END AS booked
FROM (SELECT
        EVENTS.ID AS eventId,
        VENUES.ID AS venueId,
        AREAS.ID AS areaId,
        SEATS.ID AS seatId,
        COALESCE(
            (SELECT priceId FROM PRICESDISTRIBUTION INNER JOIN PRICES
                ON PRICESDISTRIBUTION.PRICEID = PRICES.ID
                WHERE PRICESDISTRIBUTION.SEATID = SEATS.ID
                AND PRICES.EVENTID = EVENTS.ID),
            (SELECT priceId FROM PRICESDISTRIBUTION INNER JOIN PRICES
                ON PRICESDISTRIBUTION.PRICEID = PRICES.ID
                WHERE PRICESDISTRIBUTION.AREAID = AREAS.ID
                AND PRICES.EVENTID = EVENTS.ID),
            (SELECT priceId FROM PRICESDISTRIBUTION INNER JOIN PRICES
                ON PRICESDISTRIBUTION.PRICEID = PRICES.ID
                WHERE PRICESDISTRIBUTION.VENUEID = VENUES.ID
                AND PRICES.EVENTID = EVENTS.ID)
          ) AS priceId
        FROM EVENTS
            INNER JOIN VENUES ON VENUES.ID = EVENTS.VENUEID
            INNER JOIN AREAS ON AREAS.VENUEID = VENUES.ID
            INNER JOIN SEATS ON SEATS.AREAID = AREAS.ID) T
INNER JOIN PRICES ON PRICES.ID = T.priceId
INNER JOIN SEATS ON SEATS.ID = seatId;
