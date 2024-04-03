CREATE SCHEMA TKT;
SET SCHEMA TKT;

-- Create the Venue table
CREATE TABLE TKT.Venues (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    metadata CLOB,
                    svg CLOB,
                    UNIQUE (name)
);

-- Create the Area table
CREATE TABLE TKT.Areas (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    venueId VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    metadata CLOB,
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
                    metadata CLOB,
                    FOREIGN KEY (areaId) REFERENCES TKT.Areas(id),
                    UNIQUE (areaId, row, col)
);

CREATE TABLE TKT.Events (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    venueId VARCHAR(36),
                    metadata CLOB,
                    FOREIGN KEY (venueId) REFERENCES TKT.Venues(id),
                    UNIQUE(name)
);

CREATE TABLE TKT.Sessions (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    eventId VARCHAR (36) NOT NULL,
                    startTime TIMESTAMP NOT NULL,
                    endTime TIMESTAMP NOT NULL,
                    metadata CLOB,
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id),
                    UNIQUE(name, eventId)
);

CREATE PROCEDURE TKT.RaiseException(IN error VARCHAR(100))
    LANGUAGE JAVA
    PARAMETER STYLE JAVA
    NO SQL
    EXTERNAL NAME 'com.stonematrix.ticket.persist.SpExceptionRaiser.error'
;

CREATE TRIGGER TKT.PreventEventTimeOverLap
    NO CASCADE BEFORE INSERT ON TKT.Sessions
REFERENCING NEW ROW AS newRow
FOR EACH ROW MODE DB2SQL
WHEN (EXISTS (
        SELECT * FROM Sessions INNER JOIN Events ON Sessions.eventId = Events.id
            AND Events.venueId IN (SELECT venueId FROM Events WHERE eventId = newRow.eventid)
            AND NOT (startTime >= newRow.endTime OR endTime <= newRow.startTime)))
CALL RaiseException('Session time overlapping encountered within a given event')
;

--Trigger to prevent removal of paiAmount > 0 (paid order)
CREATE TRIGGER TKT.PreventPaidOrderRemoval
    NO CASCADE BEFORE DELETE ON TKT.ORDERS
REFERENCING OLD ROW AS deletedRow
FOR EACH ROW MODE DB2SQL
WHEN (deletedRow.paidAmount > 0)
    CALL RaiseException('Paid order cannot be deleted')
;

CREATE TABLE TKT.Orders (
                    id VARCHAR(36) PRIMARY KEY NOT NULL,
                    eventId VARCHAR(36) NOT NULL,
                    sessionId VARCHAR(36) NOT NULL,
                    userId VARCHAR(36) NOT NULL,
                    timestamp TIMESTAMP,
                    paidAmount DECIMAL(10, 2) DEFAULT 0,
                    metadata CLOB,
                    FOREIGN KEY (eventId) REFERENCES TKT.Events(id),
                    FOREIGN KEY (sessionId) REFERENCES TKT.Sessions(id)
);

CREATE TABLE TKT.OrderSeats (
                    orderId VARCHAR(36) NOT NULL,
                    eventId VARCHAR(36) NOT NULL,
                    sessionId VARCHAR (36) NOT NULL,
                    seatId VARCHAR(36) NOT NULL,
                    metadata CLOB,
                    PRIMARY KEY (orderId, eventId, seatId),
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
SELECT SEATS.ID, AREAID, EVENTS.ID AS EVENTID, AREAS.VENUEID, ROW, COL, AVAILABLE, SEATS.METADATA,
       PRICES.PRICE, PRICES.NAME AS PRICENAME
FROM SEATS
         INNER JOIN AREAS ON AREAS.ID = SEATS.AREAID
         INNER JOIN EVENTS ON EVENTS.VENUEID = AREAS.VENUEID
         LEFT OUTER JOIN PRICES ON PRICES.EVENTID = EVENTS.ID
    AND PRICES.ID IN (
        SELECT PRICEID FROM PRICESDISTRIBUTION
        WHERE PRICESDISTRIBUTION.SEATID = SEATS.ID)
;


CREATE VIEW TKT.SKU AS
SELECT
    T.*,
    Seats.ROW,
    Seats.COL,
    Seats.AVAILABLE,
    Seats.METADATA,
    PRICES.NAME AS priceName,
    PRICES.PRICE AS price,
    ORDERSEATS.ORDERID AS orderId
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
INNER JOIN PRICES ON PRICES.ID = T.priceId
INNER JOIN SEATS ON SEATS.ID = seatId
LEFT JOIN ORDERSEATS ON
    ORDERSEATS.EVENTID = T.eventId
    AND OrderSeats.sessionId = T.sessionId
    AND ORDERSEATS.SEATID = T.seatId
;
