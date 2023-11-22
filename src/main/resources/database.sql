CREATE SCHEMA TKT;

-- Create the Venue table
CREATE TABLE TKT.Venue (
                       id VARCHAR(36) PRIMARY KEY,
                       name VARCHAR(255) NOT NULL,
                       metadata CLOB
);

-- Create the Area table
CREATE TABLE TKT.Area (
                      id VARCHAR(36) PRIMARY KEY,
                      venueId VARCHAR(36) NOT NULL,
                      name VARCHAR(255) NOT NULL,
                      metadata CLOB,
                      FOREIGN KEY (venueId) REFERENCES TKT.Venue(id)
);

-- Create the Seat table
CREATE TABLE TKT.Seat (
                      id VARCHAR(36) PRIMARY KEY,
                      areaId VARCHAR(36) NOT NULL,
                      name VARCHAR(255) NOT NULL,
                      row INT,
                      col INT,
                      available BOOLEAN,
                      metadata CLOB,
                      FOREIGN KEY (areaId) REFERENCES TKT.Area(id)
);

CREATE TABLE TKT.Event (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    venueId VARCHAR(36),
    startTime TIMESTAMP,
    endTime TIMESTAMP,
    metadata CLOB,  
    FOREIGN KEY (venueId) REFERENCES TKT.Venue(id)
);

CREATE TABLE TKT.Order (
    id VARCHAR(36) PRIMARY KEY,
    eventId VARCHAR(36) NOT NULL,
    orderTime TIMESTAMP,
    metadata CLOB,
    FOREIGN KEY (eventId) REFERENCES TKT.Event(id)
);



