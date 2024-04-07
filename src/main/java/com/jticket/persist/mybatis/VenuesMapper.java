package com.jticket.persist.mybatis;

import com.jticket.api.model.Area;
import com.jticket.api.model.Seat;
import com.jticket.api.model.Venue;
import com.jticket.persist.mybatis.handlers.MetadataHandler;
import org.apache.ibatis.annotations.*;

import java.io.File;
import java.util.List;
import java.util.UUID;

@Mapper
public interface VenuesMapper {

    @Select("SELECT id, name, metadata FROM Venues")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Venue> loadAllVenues();

    @Select("SELECT id, name, metadata FROM Venues WHERE id = #{id}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Venue loadVenue(@Param("id") UUID venueId);

    @Select("SELECT svg FROM Venues WHERE id = #{id}")
    File loadVenueSvg(@Param("id") UUID venueId);

    @Select("SELECT id, name, metadata FROM Areas WHERE venueId = #{venueId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Area> loadAreas(@Param("venueId") String venueId);

    @Select("SELECT id, areaId, venueId, row, col, available, metadata FROM ${SEATDETAILS} WHERE venueId = #{venueId} AND areaId = #{areaId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Seat> loadSeats(@Param("venueId") UUID venueId, @Param("areaId") UUID areaId);

    @Select("SELECT venueId, name, metadata FROM Areas WHERE id = #{areaId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Area loadArea(@Param("areaId") String areaId);

    @Insert("<script>" +
            "INSERT INTO Areas (id, venueId, name, metadata) VALUES " +
            "<foreach collection='areas' item='area' separator=','> " +
            "(#{area.id}, #{area.venueId}, #{area.name}, " +
            "#{area.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler}) " +
            "</foreach>" +
            "</script>")
    void saveAreas(@Param("areas") List<Area> areas);

    @Insert("INSERT INTO Venues (id, name, metadata, svg) VALUES ( #{venue.id}, #{venue.name}, " +
            "#{venue.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler}, #{svg})")
    void saveVenue(@Param("venue") Venue venue, @Param("svg") String svg);
}
