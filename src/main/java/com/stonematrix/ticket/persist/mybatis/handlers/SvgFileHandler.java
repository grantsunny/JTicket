package com.stonematrix.ticket.persist.mybatis.handlers;

import org.apache.commons.lang.NotImplementedException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


@Component
@MappedTypes(File.class)
public class SvgFileHandler extends BaseTypeHandler<File> {

    private File generateSvgFile(String content) {
        try {
            File file = File.createTempFile("venue-template-", ".svg");
            FileOutputStream fileOut = new FileOutputStream(file, false);

            OutputStreamWriter writer = new OutputStreamWriter(fileOut, StandardCharsets.UTF_8);
            writer.write(content);
            writer.flush();
            writer.close();

            fileOut.close();
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, File parameter, JdbcType jdbcType) throws SQLException {
        throw new NotImplementedException("Writing SVG as file is not needed.");
    }

    @Override
    public File getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return (generateSvgFile(rs.getString(columnName)));
    }

    @Override
    public File getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return (generateSvgFile(rs.getString(columnIndex)));
    }

    @Override
    public File getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return (generateSvgFile(cs.getString(columnIndex)));
    }
}
