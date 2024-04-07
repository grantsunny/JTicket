package com.jticket.endpoints;

import com.jticket.persist.SeatsRepository;
import com.jticket.persist.VenuesRepository;
import com.jticket.api.VenuesApi;
import com.jticket.api.model.Area;
import com.jticket.api.model.Seat;
import com.jticket.api.model.Venue;
import com.jticket.excel.ExcelTemplateHelper;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Path("/template")
public class TemplateResource {

    @Context
    private UriInfo uriInfo;

    @Inject
    private ExcelTemplateHelper templateHelper;

    @Inject
    private VenuesRepository venuesRepository;

    @Inject
    private SeatsRepository seatsRepository;

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

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet venueSheet = workbook.getSheet("venue");
            if (venueSheet == null)
                throw new BadRequestException("Specified sheet 'venue' not found in the uploaded template");

            Sheet venueLayoutSheet = workbook.getSheet(ExcelTemplateHelper.SHEET_VENUE_LAYOUT);
            if (venueLayoutSheet == null)
                throw new BadRequestException("Specified sheet '" +  ExcelTemplateHelper.SHEET_VENUE_LAYOUT + "' not found in the uploaded template");

            Venue venue = templateHelper.parseVenue(venueSheet);
            Map<String, Area> areas = templateHelper.parseAreas(workbook, venue.getId());
            String svg = templateHelper.parseVenueSvg(venueLayoutSheet, areas);

            venuesRepository.saveVenue(venue, svg);
            venuesRepository.saveAreas(new LinkedList<>(areas.values()));

            for (String areaIndex: areas.keySet()) {
                Area area = areas.get(areaIndex);

                List<Seat> seats = templateHelper.parseSeats(workbook, areaIndex, area);
                seatsRepository.saveSeats(seats);
            }

            return venue;
        } catch (IOException e) {
            throw new BadRequestException(e.getMessage());
        } catch (SQLException e) {
            switch (e.getSQLState()) {
                case "23000":
                case "23505":
                throw new ClientErrorException("Conflict occurred", Response.Status.CONFLICT);
            default:
                throw new BadRequestException(e.getMessage());
            }
        }

    }
}
