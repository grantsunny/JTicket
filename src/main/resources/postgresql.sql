CREATE SCHEMA TKT;
SET search_path TO TKT;

DROP VIEW IF EXISTS TKT.TicketingSeatStatus;
DROP VIEW IF EXISTS TKT.SessionStatistics;
DROP VIEW IF EXISTS TKT.EventStatistics;
DROP VIEW IF EXISTS TKT.SKU;
DROP VIEW IF EXISTS TKT.SeatsInEvent;
DROP VIEW IF EXISTS TKT.SeatDetails;
DROP TRIGGER IF EXISTS prevent_paid_order_removal_trigger ON TKT.Orders;
DROP TRIGGER IF EXISTS prevent_event_time_overlap_trigger ON TKT.Sessions;
DROP FUNCTION IF EXISTS prevent_paid_order_removal();
DROP FUNCTION IF EXISTS prevent_event_time_overlap();

-- Create the Venue table
CREATE TABLE TKT.Venues (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    metadata TEXT,
                    svg TEXT,
                    UNIQUE (name)
);

-- Create the Area table
CREATE TABLE TKT.Areas (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    venueId VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    metadata TEXT,
                    FOREIGN KEY (venueId) REFERENCES TKT.Venues(id),
                    UNIQUE (venueId, name)
);

-- Create the Seat table
CREATE TABLE TKT.Seats (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    areaId VARCHAR(36) NOT NULL,
                    row INT,
                    col INT,
                    available BOOLEAN,
                    metadata TEXT,
                    FOREIGN KEY (areaId) REFERENCES TKT.Areas(id),
                    UNIQUE (areaId, row, col)
);

CREATE TABLE TKT.Events (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    venueId VARCHAR(36),
                    metadata TEXT,
                    FOREIGN KEY (venueId) REFERENCES TKT.Venues(id),
                    UNIQUE(name)
);

CREATE TABLE TKT.EventPosters (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    eventId VARCHAR(36) NOT NULL,
                    contentType VARCHAR(100) NOT NULL,
                    content BYTEA NOT NULL,
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id) ON DELETE CASCADE,
                    UNIQUE(eventId)
);

CREATE TABLE TKT.Sessions (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    eventId VARCHAR (36) NOT NULL,
                    startTime TIMESTAMP NOT NULL,
                    endTime TIMESTAMP NOT NULL,
                    metadata TEXT,
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id),
                    UNIQUE(name, eventId)
);


CREATE FUNCTION prevent_event_time_overlap()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT * FROM Sessions INNER JOIN Events ON Sessions.eventId = Events.id
            AND Events.venueId IN (SELECT venueId FROM Events WHERE id = NEW.eventid)
            AND NOT (startTime >= NEW.endTime OR endTime <= NEW.startTime)
    ) THEN
        RAISE EXCEPTION 'Session time overlapping encountered within a given event';
    END IF;
    RETURN NEW; -- Allow the insert/update
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prevent_event_time_overlap_trigger
BEFORE INSERT ON TKT.Sessions
FOR EACH ROW
EXECUTE FUNCTION prevent_event_time_overlap()
;

--Trigger to prevent removal of paymentAmount > 0 (paid order)
CREATE FUNCTION prevent_paid_order_removal()
RETURNS TRIGGER AS $$
BEGIN
    -- Check if the paymentAmount is greater than 0
    IF OLD.paymentAmount > 0 THEN
        RAISE EXCEPTION 'Paid order cannot be deleted';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE TKT.Orders (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    eventId VARCHAR(36) NOT NULL,
                    sessionId VARCHAR(36) NOT NULL,
                    userId VARCHAR(36) NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    paymentChannel VARCHAR(64),
                    paymentTransactionId VARCHAR(128),
                    paymentTimestamp TIMESTAMP,
                    paymentAmount INT DEFAULT 0,
                    metadata TEXT,
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id),
                    FOREIGN KEY (sessionId) REFERENCES TKT.Sessions(id),
                    UNIQUE (paymentTransactionId)
);

CREATE TRIGGER prevent_paid_order_removal_trigger
BEFORE DELETE ON TKT.Orders
FOR EACH ROW
EXECUTE FUNCTION prevent_paid_order_removal()
;

CREATE TABLE TKT.OrderSeats (
                    orderId VARCHAR(36) NOT NULL,
                    eventId VARCHAR(36) NOT NULL,
                    sessionId VARCHAR (36) NOT NULL,
                    seatId VARCHAR(36) NOT NULL,
                    checkedInTimestamp TIMESTAMP DEFAULT NULL, 
                    metadata TEXT,
                    PRIMARY KEY (eventId, sessionId, seatId),
                    FOREIGN KEY (orderId) REFERENCES TKT.Orders(id),
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id),
                    FOREIGN KEY (sessionId) REFERENCES TKT.Sessions(id),
                    FOREIGN KEY (seatId) REFERENCES TKT.Seats(id)
);

CREATE TABLE TKT.Prices (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    eventId VARCHAR(36) NOT NULL,
                    name VARCHAR(255),
                    price DECIMAL(10, 2),
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id),
                    UNIQUE(eventId, name)
);

CREATE TABLE TKT.PricesDistribution (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
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

CREATE VIEW TKT.SeatsInEvent AS
SELECT
    T.seatId AS id,
    T.areaId,
    T.eventId,
    T.venueId,
    Seats.ROW,
    Seats.COL,
    CASE
        WHEN Seats.AVAILABLE = FALSE THEN FALSE
        WHEN T.priceId IS NULL THEN FALSE
        ELSE TRUE
    END AS available,
    CASE
        WHEN Seats.AVAILABLE = FALSE THEN '{"ticketingAvailabilityReason":"physicalUnavailable"}'
        WHEN T.priceId IS NULL THEN '{"ticketingAvailabilityReason":"unpriced"}'
        ELSE Seats.METADATA
    END AS metadata,
    PRICES.PRICE,
    PRICES.NAME AS priceName,
    EXISTS (
        SELECT 1 FROM ORDERSEATS
        WHERE ORDERSEATS.eventId = T.eventId
        AND ORDERSEATS.seatId = T.seatId
    ) AS sold
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
INNER JOIN SEATS ON SEATS.ID = T.seatId
LEFT JOIN PRICES ON PRICES.ID = T.priceId
;

CREATE VIEW TKT.SKU AS
SELECT
    T.*,
    Seats.ROW,
    Seats.COL,
    CASE
        WHEN Seats.AVAILABLE = FALSE THEN FALSE
        WHEN T.priceId IS NULL THEN FALSE
        ELSE TRUE
    END AS available,
    CASE
        WHEN Seats.AVAILABLE = FALSE THEN '{"ticketingAvailabilityReason":"physicalUnavailable"}'
        WHEN T.priceId IS NULL THEN '{"ticketingAvailabilityReason":"unpriced"}'
        ELSE Seats.METADATA
    END AS metadata,
    PRICES.NAME AS priceName,
    PRICES.PRICE AS price,
    ORDERSEATS.ORDERID AS orderId,
    CASE
        WHEN ORDERSEATS.ORDERID IS NULL THEN FALSE
        ELSE TRUE
    END AS sold,
    ORDERSEATS.checkedInTimestamp
FROM (SELECT
        EVENTS.ID AS eventId,
        SESSIONS.ID AS sessionId,
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
        FROM SESSIONS
            INNER JOIN EVENTS ON SESSIONS.eventId = Events.id
            INNER JOIN VENUES ON VENUES.ID = EVENTS.VENUEID
            INNER JOIN AREAS ON AREAS.VENUEID = VENUES.ID
            INNER JOIN SEATS ON SEATS.AREAID = AREAS.ID) T
INNER JOIN SEATS ON SEATS.ID = seatId
LEFT JOIN PRICES ON PRICES.ID = T.priceId
LEFT JOIN ORDERSEATS ON
    ORDERSEATS.EVENTID = T.eventId
    AND OrderSeats.sessionId = T.sessionId
    AND ORDERSEATS.SEATID = T.seatId
;

CREATE VIEW TKT.EventStatistics AS
SELECT
    TKT.Events.id AS eventId,
    (SELECT COUNT(*) FROM TKT.Seats
        INNER JOIN TKT.Areas ON TKT.Seats.areaId = TKT.Areas.id
        WHERE TKT.Areas.venueId = TKT.Events.venueId) AS totalSeats,
    (SELECT COUNT(*) FROM TKT.OrderSeats
        WHERE TKT.OrderSeats.eventId = TKT.Events.id) AS orderedSeats,
    (SELECT COUNT(*) FROM TKT.OrderSeats
        WHERE TKT.OrderSeats.eventId = TKT.Events.id
        AND TKT.OrderSeats.checkedInTimestamp IS NOT NULL) AS checkedInSeats,
    (SELECT COUNT(*) FROM TKT.OrderSeats
        WHERE TKT.OrderSeats.eventId = TKT.Events.id
        AND TKT.OrderSeats.checkedInTimestamp IS NULL) AS uncheckedInSeats
FROM TKT.Events
;

CREATE VIEW TKT.SessionStatistics AS
SELECT
    TKT.Sessions.eventId,
    TKT.Sessions.id AS sessionId,
    (SELECT COUNT(*) FROM TKT.SKU
        WHERE TKT.SKU.eventId = TKT.Sessions.eventId
        AND TKT.SKU.sessionId = TKT.Sessions.id) AS totalSeats,
    (SELECT COUNT(*) FROM TKT.OrderSeats
        WHERE TKT.OrderSeats.eventId = TKT.Sessions.eventId
        AND TKT.OrderSeats.sessionId = TKT.Sessions.id) AS orderedSeats,
    (SELECT COUNT(*) FROM TKT.OrderSeats
        WHERE TKT.OrderSeats.eventId = TKT.Sessions.eventId
        AND TKT.OrderSeats.sessionId = TKT.Sessions.id
        AND TKT.OrderSeats.checkedInTimestamp IS NOT NULL) AS checkedInSeats,
    (SELECT COUNT(*) FROM TKT.OrderSeats
        WHERE TKT.OrderSeats.eventId = TKT.Sessions.eventId
        AND TKT.OrderSeats.sessionId = TKT.Sessions.id
        AND TKT.OrderSeats.checkedInTimestamp IS NULL) AS uncheckedInSeats
FROM TKT.Sessions
;

CREATE VIEW TKT.TicketingSeatStatus AS
SELECT
    TKT.SKU.eventId,
    TKT.SKU.sessionId,
    TKT.SKU.venueId,
    TKT.SKU.areaId,
    TKT.SKU.seatId,
    TKT.SKU.row,
    TKT.SKU.col,
    TKT.SKU.available,
    TKT.SKU.metadata,
    TKT.SKU.priceName,
    TKT.SKU.price,
    TKT.SKU.orderId,
    TKT.Orders.userId,
    TKT.OrderSeats.checkedInTimestamp,
    CASE
        WHEN TKT.SKU.orderId IS NULL THEN 'open'
        WHEN TKT.OrderSeats.checkedInTimestamp IS NOT NULL THEN 'checkedIn'
        ELSE 'booked'
    END AS status
FROM TKT.SKU
LEFT JOIN TKT.Orders ON TKT.Orders.id = TKT.SKU.orderId
LEFT JOIN TKT.OrderSeats ON
    TKT.OrderSeats.eventId = TKT.SKU.eventId
    AND TKT.OrderSeats.sessionId = TKT.SKU.sessionId
    AND TKT.OrderSeats.seatId = TKT.SKU.seatId
;
