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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String metadataMapToJsonString(Map<String, Object> metadataMap) {
        try {
            return OBJECT_MAPPER.writeValueAsString(metadataMap);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String rawMetadata) {
        if (rawMetadata == null || rawMetadata.isBlank())
            return new HashMap<>();

        try {
            return OBJECT_MAPPER.readValue(
                    rawMetadata,
                    Map.class);
        } catch (JsonProcessingException ex) {
            return new HashMap<>();
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        String jsonString = metadataMapToJsonString(parameter);
        ps.setString(i, jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        if (!columnName.equalsIgnoreCase("metadata"))
            throw new SQLException("Unexpected metadata mapping for column " + columnName);

        String jsonString = rs.getString(columnName);
        return parseMetadata(jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        if (!rs.getMetaData().getColumnName(columnIndex).equalsIgnoreCase("metadata"))
            throw new SQLException("Unexpected metadata mapping for column " + rs.getMetaData().getColumnName(columnIndex));

        String jsonString = rs.getString(columnIndex);
        return parseMetadata(jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        if (!cs.getMetaData().getColumnName(columnIndex).equalsIgnoreCase("metadata"))
            throw new SQLException("Unexpected metadata mapping for column " + cs.getMetaData().getColumnName(columnIndex));

        String jsonString = cs.getString(columnIndex);
        return parseMetadata(jsonString);
    }
}
