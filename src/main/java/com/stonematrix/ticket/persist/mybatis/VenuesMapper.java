package com.stonematrix.ticket.persist.mybatis;

import com.stonematrix.ticket.api.model.Area;
import com.stonematrix.ticket.api.model.Seat;
import com.stonematrix.ticket.api.model.Venue;
import org.apache.ibatis.annotations.*;

import java.io.File;
import java.util.List;
import java.util.UUID;

@Mapper
public interface VenuesMapper {

    @Select("SELECT id, name, metadata FROM TKT.Venues")
    List<Venue> loadAllVenues();
    @Select("SELECT id, name, metadata FROM TKT.Venues WHERE id = #{id}")
    Venue loadVenue(@Param("id") UUID venueId);
    @Select("SELECT svg FROM TKT.Venues WHERE id = #{id}")
    File loadVenueSvg(@Param("id") UUID venueId);
    @Select("SELECT id, name, metadata FROM TKT.Areas WHERE venueId = #{venueId}")
    List<Area> loadAreas(@Param("venueId") String venueId);
    @Select("SELECT id, areaId, venueId, row, col, available, metadata FROM TKT.SeatDetails WHERE venueId = #{venueId} AND areaId = #{areaId}")
    List<Seat> loadSeats(@Param("venueId") UUID venueId, @Param("areaId") UUID areaId);
    @Select("SELECT venueId, name, metadata FROM TKT.Areas WHERE id = #{areaId}")
    Area loadArea(@Param("areaId") String areaId);
    @Insert("<script>" +
            "INSERT INTO TKT.Areas (id, venueId, name, metadata) VALUES " +
            "<foreach collection='areas' item='area' separator=','> " +
            "(#{area.id}, #{area.venueId}, #{area.name}, #{area.metadata}) " +
            "</foreach>" +
            "</script>")
    void saveAreas(@Param("areas") List<Area> areas);
    @Insert("INSERT INTO TKT.Venues (id, name, metadata, svg) VALUES ( #{venue.id}, #{venue.name}, #{venue.metadata}, #{svg})")
    void saveVenue(@Param("venue") Venue venue, @Param("svg") String svg);
}
