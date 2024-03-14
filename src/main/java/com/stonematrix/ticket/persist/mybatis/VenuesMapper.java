package com.stonematrix.ticket.persist.mybatis;

import com.stonematrix.ticket.api.model.Area;
import com.stonematrix.ticket.api.model.Seat;
import com.stonematrix.ticket.api.model.Venue;
import com.stonematrix.ticket.persist.PersistenceException;
import org.apache.ibatis.annotations.*;

import java.io.File;
import java.util.List;
import java.util.UUID;

@Mapper
public interface VenuesMapper {

    @Select("SELECT id, name, metadata FROM TKT.Venues")
    List<Venue> loadAllVenues() throws PersistenceException;
    @Select("SELECT id, name, metadata FROM TKT.Venues WHERE id = #{id}")
    Venue loadVenue(@Param("id") UUID venueId) throws PersistenceException;
    @Select("SELECT svg FROM TKT.Venues WHERE id = #{id}")
    File loadVenueSvg(@Param("id") UUID venueId) throws PersistenceException;
    @Select("SELECT id, name, metadata FROM TKT.Areas WHERE venueId = #{venueId}")
    List<Area> loadAreas(@Param("venueId") String venueId) throws PersistenceException;
    @Select("SELECT id, areaId, venueId, row, col, available, metadata FROM TKT.SeatDetails WHERE venueId = #{venueId} AND areaId = #{areaId}")
    List<Seat> loadSeats(@Param("venueId") UUID venueId, @Param("areaId") UUID areaId) throws PersistenceException;
    @Select("SELECT venueId, name, metadata FROM TKT.Areas WHERE id = #{areaId}")
    Area loadArea(@Param("areaId") String areaId) throws PersistenceException;
    @Insert("<script>" +
            "INSERT INTO TKT.Areas (id, venueId, name, metadata) VALUES " +
            "<foreach collection='areas' item='area' separator=','> " +
            "(#{area.id}, #{area.venueId}, #{area.name}, #{area.metadata}) " +
            "</foreach>" +
            "</script>")
    void saveAreas(@Param("areas") List<Area> areas) throws PersistenceException;
    @Insert("INSERT INTO TKT.Venues (id, name, metadata, svg) VALUES ( #{venue.id}, #{venue.name}, #{venue.metadata}, #{svg})")
    void saveVenue(@Param("venue") Venue venue, @Param("svg") String svg) throws PersistenceException;
}
