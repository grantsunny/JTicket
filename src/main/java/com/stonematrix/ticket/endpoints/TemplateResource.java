package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.VenuesApi;
import com.stonematrix.ticket.api.model.Venue;
import com.stonematrix.ticket.persist.JdbcHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("/template")
public class TemplateResource {

    @Inject
    private JdbcHelper jdbc;

    @Context
    private UriInfo uriInfo;
    @GET
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response downloadTemplate() {
        InputStream fileStream = getClass().getClassLoader().getResourceAsStream("template.xlsx");
        return Response.ok(fileStream)
                .header("Content-Disposition", "attachment; filename=\"template.xlsx\"")
                .build();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadTemplate(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition fileMetaData) {

        String fileName = fileMetaData.getFileName();
        if (fileName == null || !fileName.endsWith(".xlsx")) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid file type. Please upload an .xlsx file.").build();
        }

        // Logic to process the spreadsheet and create the Venue
        Venue newVenue = processSpreadsheetAndCreateVenue(fileInputStream); // Implement this method

        // Construct the URL for the new Venue
        UriBuilder uriBuilder =
                uriInfo.getBaseUriBuilder().
                        path(VenuesApi.class).
                        path(String.valueOf(newVenue.getId()));

        return Response.created(uriBuilder.build()).build();
    }

    private Venue processSpreadsheetAndCreateVenue(InputStream inputStream) {
        // Implement the logic to process the spreadsheet and create a venue
        // Return the created Venue object

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet venueSheet = workbook.getSheet("venue");
            String venueName = null;
            Map<String, Object> venueMetadata = new HashMap<>();

            // Extracting the venue name from the first row
            Row firstRow = venueSheet.getRow(0);
            if (firstRow != null) {
                Cell nameCell = firstRow.getCell(1); // Assuming the name is in the second column
                venueName = nameCell.getStringCellValue();
            }

            // Extracting metadata from the subsequent rows
            for (Row row : venueSheet) {
                if (row.getRowNum() == 0) continue; // Skip the first row

                Cell keyCell = row.getCell(0);
                Cell valueCell = row.getCell(1);

                if (keyCell != null && valueCell != null) {
                    String key = keyCell.getStringCellValue();
                    String value = valueCell.getStringCellValue();
                    venueMetadata.put(key, value);
                }
            }

            Venue venue = new Venue()
                    .id(UUID.randomUUID())
                    .name(venueName)
                    .metadata(venueMetadata);

            jdbc.saveVenue(venue);
            return venue;

        } catch (IOException | SQLException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
