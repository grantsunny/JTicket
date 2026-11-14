DROP VIEW TKT.SessionStatistics;;
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
;;

DROP VIEW TKT.TicketingSeatStatus;;
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
;;
