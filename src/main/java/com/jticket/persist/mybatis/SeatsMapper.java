package com.jticket.persist.mybatis;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import com.jticket.api.model.Seat;
import com.jticket.persist.mybatis.handlers.MetadataHandler;

@Mapper
public interface SeatsMapper {
    @Select("SELECT id, areaId, venueId, row, col, available, metadata FROM ${SEATDETAILS} WHERE venueId = #{veuneId} AND areaId = #{areaId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Seat> loadSeats(@Param("venueId") UUID venueId, @Param("areaId") UUID areaId);

    @Select("SELECT areaId, Areas.venueId, row, col, available, Seats.metadata FROM Seats " +
            "INNER JOIN Areas ON Areas.id = Seats.areaId " +
            "AND Seats.id = #{seatId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Seat loadSeat(@Param("seatId") UUID seatId);
    
    @Select("SELECT areaId, Areas.venueId, row, col, available, Seats.metadata FROM Seats " +
            "INNER JOIN Areas ON Areas.id = Seats.areaId " +
            "AND Seats.id = #{seatId} AND Areas.venueId = #{venueId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})    
    Seat loadSeat(@Param("venueId") UUID venueId, @Param("seatId") UUID seatId);

    @Select("SELECT id, areaId, venueId, row, col, available, metadata " +
            "FROM ${SEATDETAILS} WHERE venueId = #{venueId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Seat> loadSeatsByVenue(@Param("venueId") UUID venueId);

    @Insert("<script>" +
            "INSERT INTO Seats (id, areaId, row, col, available, metadata) VALUES " +
            "<foreach collection='seats' item='seat' separator=','> " +
            "(#{seat.id}, #{seat.areaId}, #{seat.row}, #{seat.col}, #{seat.available}, " +
            "#{seat.metadata, typeHandler=com.jticket.persist.mybatis.handlers.MetadataHandler})" +
            "</foreach>" +
            "</script>")
    void saveSeats(List<Seat> seats);
}
