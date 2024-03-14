package com.stonematrix.ticket.persist.mybatis.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
@Component
@MappedTypes(Map.class)
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
        String jsonString = rs.getString(columnName);
        return parseMetadata(jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String jsonString = rs.getString(columnIndex);
        return parseMetadata(jsonString);
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String jsonString = cs.getString(columnIndex);
        return parseMetadata(jsonString);
    }
}
