package com.stonematrix.ticket.svg;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class SvgHelper {
    public static final String SHEET_VENUE_LAYOUT = "venue-layout";

    public static String convertExcelWorkbookToSvg(String excelFilePath, String sheetName) throws IOException {
        try (FileInputStream excelFile = new FileInputStream(excelFilePath)) {
            return generateSvgFromWorkbook(new XSSFWorkbook(excelFile), sheetName);
        }
    }

    public static String convertExcelWorkbookToSvg(InputStream excelFileStream, String sheetName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(excelFileStream)) {
            return generateSvgFromWorkbook(workbook, sheetName);
        }
    }

    public static void convertExcelWorkbookToSvgAsStream(String excelFilePath, String sheetName, OutputStream outputStream) throws IOException {
        try (FileInputStream excelFile = new FileInputStream(excelFilePath)) {
            writeSvgFromWorkbook(new XSSFWorkbook(excelFile), sheetName, outputStream);
        }
    }

    public static void convertExcelWorkbookToSvgAsStream(InputStream excelFileStream, String sheetName, OutputStream outputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(excelFileStream)) {
            writeSvgFromWorkbook(workbook, sheetName, outputStream);
        }
    }

    private static void writeSvgFromWorkbook(Workbook workbook, String sheetName, OutputStream outputStream) throws IOException {
        String svgContent = generateSvgFromWorkbook(workbook, sheetName);
        outputStream.write(svgContent.getBytes(StandardCharsets.UTF_8));
    }

    private static String generateSvgFromWorkbook(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);

        StringBuilder svgContent = new StringBuilder();
        svgContent.append("<svg width=\"100%\" height=\"100%\" xmlns=\"http://www.w3.org/2000/svg\">\n");

        int cellWidth = 20;
        int cellHeight = 20;

        for (Row row : sheet) {
            for (Cell cell : row) {
                String metadata = cell.getStringCellValue();
                String fillColor = "none";
                if (cell.getCellStyle().getFillForegroundColorColor() != null) {
                    Color color = cell.getCellStyle().getFillForegroundColorColor();
                    fillColor = color instanceof XSSFColor ? ((XSSFColor) color).getARGBHex().substring(2) : fillColor;
                }

                int x = cell.getColumnIndex() * cellWidth;
                int y = cell.getRowIndex() * cellHeight;

                svgContent.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#%s\" metadata=\"%s\" />\n",
                        x, y, cellWidth, cellHeight, fillColor, metadata));
            }
        }

        svgContent.append("</svg>");
        return svgContent.toString();
    }
}
