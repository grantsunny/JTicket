package com.stonematrix.ticket.persist.mybatis;

import com.stonematrix.ticket.api.model.Seat;
import com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SeatsMapper {
    @Select("SELECT id, areaId, venueId, row, col, available, metadata FROM TKT.SeatDetails WHERE venueId = #{veuneId} AND areaId = #{areaId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Seat> loadSeats(@Param("venueId") UUID venueId, @Param("areaId") UUID areaId);

    @Select("SELECT areaId, TKT.Areas.venueId, row, col, available, TKT.Seats.metadata FROM TKT.Seats " +
            "INNER JOIN TKT.Areas ON TKT.Areas.id = TKT.Seats.areaId " +
            "AND TKT.Seats.id = #{seatId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    Seat loadSeat(@Param("seatId") UUID seatId);

    @Select("SELECT id, areaId, venueId, row, col, available, metadata " +
            "FROM TKT.SeatDetails WHERE venueId = #{venueId}")
    @Results({@Result(property = "metadata", column = "metadata", typeHandler = MetadataHandler.class)})
    List<Seat> loadSeatsByVenue(@Param("venueId") UUID venueId);

    @Insert("<script>" +
            "INSERT INTO TKT.Seats (id, areaId, row, col, available, metadata) " +
            "<foreach collection='seats' item='seat' separator=','> " +
            "(#{seat}, #{seat.areaId}, #{seat.row}, #{seat.col}, #{seat.available}, " +
            "#{seat.metadata, typeHandler=com.stonematrix.ticket.persist.mybatis.handlers.MetadataHandler}) " +
            "</foreach>" +
            "</script>")
    void saveSeats(List<Seat> seats);
}
