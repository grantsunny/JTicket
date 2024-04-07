package com.jticket.persist.mybatis.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@MappedTypes({Map.class, HashMap.class, LinkedHashMap.class})
@MappedJdbcTypes(JdbcType.CLOB)
public class MetadataHandler extends BaseTypeHandler<Map<String, Object>> {

    private String metadataMapToJsonString(Map<String, Object> metadataMap) {
        String metadataJson;
        try {
            metadataJson = new ObjectMapper().writeValueAsString(metadataMap);
        } catch (JsonProcessingException e) {
            metadataJson = "{}";
        }
        return metadataJson;
    }

    private Map<String, Object> parseMetadata(String rawMetadata) {
        Map<String, Object> metadata;
        try {
            metadata = new ObjectMapper().readValue(
                    rawMetadata,
                    Map.class);
        } catch (JsonProcessingException ex) {
            metadata = new HashMap<>();
        }
        return metadata;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        String jsonString = metadataMapToJsonString(parameter);
        ps.setString(i, jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        if (!columnName.equalsIgnoreCase("metadata"))
            throw new SQLException("FIXME: Unexpected mapping handling here for column " + columnName);

        String jsonString = rs.getString(columnName);
        return parseMetadata(jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        if (!rs.getMetaData().getColumnName(columnIndex).equalsIgnoreCase("metadata"))
            throw new SQLException("FIXME: Unexpected mapping handling here for column " + rs.getMetaData().getColumnName(columnIndex));

        String jsonString = rs.getString(columnIndex);
        return parseMetadata(jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        if (!cs.getMetaData().getColumnName(columnIndex).equalsIgnoreCase("metadata"))
            throw new SQLException("FIXME: Unexpected mapping handling here for column " + cs.getMetaData().getColumnName(columnIndex));

        String jsonString = cs.getString(columnIndex);
        return parseMetadata(jsonString);
    }
}
