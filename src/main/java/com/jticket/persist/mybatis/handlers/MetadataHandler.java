package com.jticket.persist.mybatis.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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

    private Map<String, Object> parseMetadata(String rawMetadata) {
        if (rawMetadata == null || rawMetadata.isBlank()) {
            return new HashMap<>();
        }

        try {
            return OBJECT_MAPPER.readValue(
                    rawMetadata,
                    new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            return new HashMap<>();
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, metadataMapToJsonString(parameter));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        if (!columnName.equalsIgnoreCase("metadata")) {
            throw new SQLException("Unexpected metadata mapping for column " + columnName);
        }

        return parseMetadata(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String columnName = rs.getMetaData().getColumnName(columnIndex);
        if (!columnName.equalsIgnoreCase("metadata")) {
            throw new SQLException("Unexpected metadata mapping for column " + columnName);
        }

        return parseMetadata(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String columnName = cs.getMetaData().getColumnName(columnIndex);
        if (!columnName.equalsIgnoreCase("metadata")) {
            throw new SQLException("Unexpected metadata mapping for column " + columnName);
        }

        return parseMetadata(cs.getString(columnIndex));
    }
}
