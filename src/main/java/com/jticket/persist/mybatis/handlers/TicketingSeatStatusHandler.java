package com.jticket.persist.mybatis.handlers;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import com.jticket.api.model.TicketingSeat;

public class TicketingSeatStatusHandler extends BaseTypeHandler<TicketingSeat.StatusEnum> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, TicketingSeat.StatusEnum parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.value());
    }

    @Override
    public TicketingSeat.StatusEnum getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return status(rs.getString(columnName));
    }

    @Override
    public TicketingSeat.StatusEnum getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return status(rs.getString(columnIndex));
    }

    @Override
    public TicketingSeat.StatusEnum getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return status(cs.getString(columnIndex));
    }

    private TicketingSeat.StatusEnum status(String value) {
        return value == null ? null : TicketingSeat.StatusEnum.fromValue(value);
    }
}
