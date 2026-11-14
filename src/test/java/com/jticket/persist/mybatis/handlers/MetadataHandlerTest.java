package com.jticket.persist.mybatis.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MetadataHandlerTest {

    private final MetadataHandler handler = new MetadataHandler();

    @Test
    void returnsEmptyMetadataForNullBlankAndInvalidJson() throws Exception {
        assertThat(readMetadata(null)).isEmpty();
        assertThat(readMetadata("   ")).isEmpty();
        assertThat(readMetadata("{not-json")).isEmpty();
    }

    @Test
    void serializesMetadataWhenBindingParameter() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 2, Map.of("campaign", "launch"), null);

        verify(statement).setString(2, "{\"campaign\":\"launch\"}");
    }

    private Map<String, Object> readMetadata(String rawMetadata) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnName(1)).thenReturn("metadata");
        when(resultSet.getString(1)).thenReturn(rawMetadata);

        return handler.getNullableResult(resultSet, 1);
    }
}
