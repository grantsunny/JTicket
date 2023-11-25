package com.stonematrix.ticket.excel;

import com.stonematrix.ticket.api.model.Area;
import com.stonematrix.ticket.api.model.Seat;
import com.stonematrix.ticket.api.model.Venue;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ExcelTemplateHelper {

    public static final String SHEET_VENUE_LAYOUT = "venue-layout";

    public Map<String, Area> parseAreas(Workbook workbook, UUID venueId) {
        Map<String, Area> areas = new HashMap<>();
        for (Iterator<Sheet> it = workbook.sheetIterator(); it.hasNext(); ) {
            Sheet sheet = it.next();
            String sheetName = sheet.getSheetName();
            if (sheetName.startsWith("area")) {
                if (!sheetName.endsWith("seats")) {
                    String areaIndex = sheetName.substring("area".length()).trim();
                    String areaName;
                    Map<String, Object> metadata = new HashMap<>();

                    Row firstRow = sheet.getRow(0);
                    if (firstRow != null) {
                        Cell nameCell = firstRow.getCell(1); // Assuming the name is in the second column
                        areaName = nameCell.getStringCellValue();
                    } else
                        throw new IllegalArgumentException("First row is missing in sheet " + sheetName);

                    // Extracting metadata from the subsequent rows
                    parseMetadata(sheet, metadata);
                    areas.put(areaIndex, new Area().id(UUID.randomUUID())
                            .venueId(venueId)
                            .name(areaName)
                            .metadata(metadata));
                }
            }
        }
        return areas;
    }

    private void parseMetadata(Sheet sheet, Map<String, Object> metadata) {
        metadata.clear();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) continue; // Skip the first row

            Cell keyCell = row.getCell(0);
            Cell valueCell = row.getCell(1);

            if (keyCell != null && valueCell != null) {
                String key = keyCell.getStringCellValue();
                String value = valueCell.getStringCellValue();
                metadata.put(key, value);
            }
        }
    }


    public String parseVenueSvg(Sheet sheet, Map<String, Area> areas) {
        StringBuilder svgContent = new StringBuilder();
        svgContent.append("<svg preserveAspectRatio=\"xMidYMid meet\" style=\"display: block; max-width: 100%; height: auto; margin: auto;\" xmlns=\"http://www.w3.org/2000/svg\">\n");

        int cellWidth = 20;
        int cellHeight = 20;

        for (Row row : sheet) {
            for (Cell cell : row) {
                DataFormatter formatter = new DataFormatter();
                String areaIndex = formatter.formatCellValue(cell);

                String fillColor = "none";
                if (cell.getCellStyle().getFillForegroundColorColor() != null) {
                    Color color = cell.getCellStyle().getFillForegroundColorColor();
                    fillColor = color instanceof XSSFColor ? ((XSSFColor) color).getARGBHex().substring(2) : fillColor;
                }

                int x = cell.getColumnIndex() * cellWidth;
                int y = cell.getRowIndex() * cellHeight;

                UUID areaId = areas.get(areaIndex).getId();
                svgContent.append(String.format("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"#%s\" areaId=\"%s\" />\n",
                        x, y, cellWidth, cellHeight, fillColor, areaId));
            }
        }

        svgContent.append("</svg>");
        return svgContent.toString();
    }

    public Venue parseVenue(Sheet venueSheet) {
        String venueName = null;
        Map<String, Object> venueMetadata = new HashMap<>();

        // Extracting the venue name from the first row
        Row firstRow = venueSheet.getRow(0);
        if (firstRow != null) {
            Cell nameCell = firstRow.getCell(1); // Assuming the name is in the second column
            venueName = nameCell.getStringCellValue();
        }

        // Extracting metadata from the subsequent rows
        parseMetadata(venueSheet, venueMetadata);

        return new Venue()
                .id(UUID.randomUUID())
                .name(venueName)
                .metadata(venueMetadata);
    }

    public List<Seat> parseSeats(Workbook workbook, String areaIndex, Area area) {

        String sheetName = "area" + areaIndex + "-seats";
        Sheet sheetSeats = workbook.getSheet(sheetName);

        List<Seat> seats = new LinkedList<>();
        for (Row row : sheetSeats) {
            for (Cell cell : row) {
                int iRow = cell.getRowIndex();
                int iCol = cell.getColumnIndex();
                String cellValue = cell.getStringCellValue();
                boolean available = cellValue.equalsIgnoreCase("Y");

                seats.add(new Seat().id(UUID.randomUUID())
                        .areaId(area.getId())
                        .row(iRow)
                        .column(iCol)
                        .available(available));
            }
        }
        return seats;
    }
}
