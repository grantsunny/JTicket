-- Create the Venue table
CREATE TABLE TKT.Venues (
                            id VARCHAR(36) PRIMARY KEY NOT NULL,
                            name VARCHAR(255) NOT NULL,
                            metadata VARCHAR(4096),
                            svg VARCHAR(65536)
--                             UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_venues_name ON VENUES (`name`);

-- Create the Area table
CREATE TABLE TKT.Areas (
                           id VARCHAR(36) PRIMARY KEY NOT NULL,
                           venueId VARCHAR(36) NOT NULL,
                           name VARCHAR(255) NOT NULL,
                           metadata VARCHAR(4096)
--                            FOREIGN KEY (venueId) REFERENCES TKT.Venues(id)
--                            UNIQUE (venueId, name)
);

CREATE INDEX IF NOT EXISTS idx_areas_venueId_name ON AREAS (venueId, `name`);
CREATE INDEX IF NOT EXISTS idx_areas_venueId ON AREAS (venueId);

-- Create the Seat table
CREATE TABLE TKT.Seats (
                           id VARCHAR(36) PRIMARY KEY NOT NULL,
                           areaId VARCHAR(36) NOT NULL,
                           row INT,
                           col INT,
                           available BOOLEAN,
                           metadata VARCHAR(4096)
--                            FOREIGN KEY (areaId) REFERENCES TKT.Areas(id)
--                            UNIQUE (areaId, row, col)
);

CREATE INDEX IF NOT EXISTS idx_seats_areaId ON Seats (areaId);

CREATE TABLE TKT.Events (
                            id VARCHAR(36) PRIMARY KEY NOT NULL,
                            name VARCHAR(255) NOT NULL,
                            venueId VARCHAR(36),
                            metadata VARCHAR(4096)
--                             FOREIGN KEY (venueId) REFERENCES TKT.Venues(id)
--                             UNIQUE(name)
);

CREATE INDEX IF NOT EXISTS idx_events_venueId ON Events (venueId);
CREATE INDEX IF NOT EXISTS idx_events_name ON Events (`name`);

CREATE TABLE TKT.Sessions (
                              id VARCHAR(36) PRIMARY KEY NOT NULL,
                              name VARCHAR(255) NOT NULL,
                              eventId VARCHAR (36) NOT NULL,
                              startTime TIMESTAMP NOT NULL,
                              endTime TIMESTAMP NOT NULL,
                              metadata VARCHAR(4096)
--                               FOREIGN KEY (eventId) REFERENCES TKT.Events(id)
--                               UNIQUE(name, eventId)
);

CREATE INDEX IF NOT EXISTS idx_sessions_eventId ON Sessions (eventId);
CREATE INDEX IF NOT EXISTS idx_sessions_eventId_name ON Sessions (eventId, `name`);

-- CREATE PROCEDURE TKT.RaiseException(IN error VARCHAR(100))
--     LANGUAGE JAVA
--     PARAMETER STYLE JAVA
--     NO SQL
--     EXTERNAL NAME 'persist.com.jticket.SpExceptionRaiser.error'
-- ;
--
-- CREATE TRIGGER TKT.PreventEventTimeOverLap
--     NO CASCADE BEFORE INSERT ON TKT.Sessions
--     REFERENCING NEW ROW AS newRow
--     FOR EACH ROW MODE DB2SQL
--     WHEN (EXISTS (
--         SELECT * FROM Sessions INNER JOIN Events ON Sessions.eventId = Events.id
--             AND Events.venueId IN (SELECT venueId FROM Events WHERE eventId = newRow.eventid)
--             AND NOT (startTime >= newRow.endTime OR endTime <= newRow.startTime)))
--     CALL RaiseException('Session time overlapping encountered within a given event')
-- ;
--
-- --Trigger to prevent removal of paiAmount > 0 (paid order)
-- CREATE TRIGGER TKT.PreventPaidOrderRemoval
--     NO CASCADE BEFORE DELETE ON TKT.ORDERS
--     REFERENCING OLD ROW AS deletedRow
--     FOR EACH ROW MODE DB2SQL
--     WHEN (deletedRow.paidAmount > 0)
--     CALL RaiseException('Paid order cannot be deleted')
-- ;

CREATE TABLE TKT.Orders (
                            id VARCHAR(36) PRIMARY KEY NOT NULL,
                            eventId VARCHAR(36) NOT NULL,
                            sessionId VARCHAR(36) NOT NULL,
                            userId VARCHAR(36) NOT NULL,
                            timestamp TIMESTAMP,
                            paidAmount DECIMAL(10, 2) DEFAULT 0,
                            metadata VARCHAR(4096)
--                             FOREIGN KEY (eventId) REFERENCES TKT.Events(id),
--                             FOREIGN KEY (sessionId) REFERENCES TKT.Sessions(id)
);

CREATE INDEX IF NOT EXISTS idx_orders_eventId ON Orders (eventId);
CREATE INDEX IF NOT EXISTS idx_orders_sessionId ON Orders (sessionId);

CREATE TABLE TKT.OrderSeats (
                                orderId VARCHAR(36) NOT NULL,
                                eventId VARCHAR(36) NOT NULL,
                                sessionId VARCHAR (36) NOT NULL,
                                seatId VARCHAR(36) NOT NULL,
                                checkin BOOLEAN,
                                metadata VARCHAR(4096),
                                PRIMARY KEY (orderId, eventId, seatId)
--                                 FOREIGN KEY (orderId) REFERENCES TKT.Orders(id),
--                                 FOREIGN KEY (eventId) REFERENCES TKT.Events(id),
--                                 FOREIGN KEY (sessionId) REFERENCES TKT.Sessions(id),
--                                 FOREIGN KEY (seatId) REFERENCES TKT.Seats(id)
) WITH "affinity_key=orderId";

CREATE INDEX IF NOT EXISTS idx_orderseats_sessionId ON OrderSeats (sessionId);
CREATE INDEX IF NOT EXISTS idx_orderseats_orderId ON OrderSeats (orderId);
CREATE INDEX IF NOT EXISTS idx_orderseats_eventId ON OrderSeats (eventId);
CREATE INDEX IF NOT EXISTS idx_orderseats_seatId ON OrderSeats (seatId);

CREATE TABLE TKT.Prices (
                            id VARCHAR(36) PRIMARY KEY NOT NULL,
                            eventId VARCHAR(36) NOT NULL,
                            name VARCHAR(255),
                            price DECIMAL(10, 2)
--                             FOREIGN KEY (eventId) REFERENCES TKT.Events(id)
--                             UNIQUE(eventId, name)
);

CREATE INDEX IF NOT EXISTS idex_prices_eventId ON Prices (eventId);

CREATE TABLE TKT.PricesDistribution (
                                        id VARCHAR(36) PRIMARY KEY NOT NULL,
                                        priceId VARCHAR(36) NOT NULL,
                                        seatId VARCHAR(36),
                                        areaId VARCHAR(36),
                                        venueId VARCHAR(36)
--                                         FOREIGN KEY (priceId) REFERENCES TKT.Prices(id),
--                                         FOREIGN KEY (seatId) REFERENCES TKT.Seats(id),
--                                         FOREIGN KEY (areaId) REFERENCES TKT.Areas(id),
--                                         FOREIGN KEY (venueId) REFERENCES TKT.Venues(id)
--                                         UNIQUE(priceId, seatId),
--                                         UNIQUE(priceId, areaId),
--                                         UNIQUE(priceId, venueId)
);

CREATE INDEX IF NOT EXISTS idx_pricesDistribution_priceId ON PricesDistribution (priceId);
CREATE INDEX IF NOT EXISTS idx_pricesDistribution_seatId ON PricesDistribution (seatId);
CREATE INDEX IF NOT EXISTS idx_pricesDistribution_areaId ON PricesDistribution (areaId);
CREATE INDEX IF NOT EXISTS idx_pricesDistribution_venueId ON PricesDistribution (venueId);

CREATE INDEX IF NOT EXISTS idx_pricesDistribution_priceId_seatId ON PricesDistribution (priceId, seatId);
CREATE INDEX IF NOT EXISTS idx_pricesDistribution_priceId_areaId ON PricesDistribution (priceId, areaId);
CREATE INDEX IF NOT EXISTS idx_pricesDistribution_priceId_venueId ON PricesDistribution (priceId, venueId);
