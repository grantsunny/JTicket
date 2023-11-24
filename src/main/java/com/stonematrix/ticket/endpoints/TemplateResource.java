package com.stonematrix.ticket.endpoints;

import com.stonematrix.ticket.api.VenuesApi;
import com.stonematrix.ticket.api.model.Venue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.*;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import java.io.InputStream;
import java.util.UUID;

@Path("/template")
public class TemplateResource {

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

    private Venue processSpreadsheetAndCreateVenue(InputStream fileInputStream) {
        // Implement the logic to process the spreadsheet and create a venue
        // Return the created Venue object

        //TODO: implement the parsing and filling of Derby tables.

        return new Venue()
                .id(UUID.randomUUID())
                .name("A Dummy Venue!");
    }
}
