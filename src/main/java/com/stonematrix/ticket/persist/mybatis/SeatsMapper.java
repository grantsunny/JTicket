package com.stonematrix.ticket.persist.mybatis;

import com.stonematrix.ticket.api.model.Seat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface SeatsMapper {
    @Select("SELECT id, areaId, venueId, row, col, available, metadata FROM TKT.SeatDetails WHERE venueId = #{veuneId} AND areaId = #{areaId}")
    List<Seat> loadSeats(@Param("venueId") UUID venueId, @Param("areaId") UUID areaId);

    @Select("SELECT areaId, TKT.Areas.venueId, row, col, available, TKT.Seats.metadata FROM TKT.Seats " +
            "INNER JOIN TKT.Areas ON TKT.Areas.id = TKT.Seats.areaId " +
            "AND TKT.Seats.id = #{seatId}")
    Seat loadSeat(@Param("seatId") UUID seatId);

    @Select("SELECT id, areaId, venueId, row, col, available, metadata " +
            "FROM TKT.SeatDetails WHERE venueId = #{venueId}")
    List<Seat> loadSeats(@Param("venueId") UUID venueId);

    @Insert("<script>" +
            "INSERT INTO TKT.Seats (id, areaId, row, col, available, metadata) " +
            "<foreach collection='seats' item='seat' separator=','> " +
            "(#{seat}, #{seat.areaId}, #{seat.row}, #{seat.col}, #{seat.available}, #{seat.metadata}) " +
            "</foreach>" +
            "</script>")
    void saveSeats(List<Seat> seats);
}
