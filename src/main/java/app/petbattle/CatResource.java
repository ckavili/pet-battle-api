package app.petbattle;

import app.petbattle.rest.client.NSFWTransactionService;
import app.petbattle.rest.client.TransactionRequest;
import app.petbattle.rest.client.TransactionResponse;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;

@Path("/cats")
@Consumes("application/json")
@Produces("application/json")
public class CatResource {

    private final Logger log = LoggerFactory.getLogger(CatResource.class);

    @Inject
    @RestClient
    NSFWTransactionService nsfwTransactionService;

    /**
     * List all Cats including images
     * @return
     */
    @GET
    @Operation(operationId = "list",
            summary = "get all cats",
            description = "This operation retrieves all cats from the database that are safe for work",
            deprecated = false,
            hidden = false)
    public List<Cat> list() {
        return Cat.find("issfw = true").list();
    }

    /**
     * Just return all Cat ids not images
     * @return
     */
    @GET
    @Path("/ids")
    @Operation(operationId = "ids",
            summary = "get all cat ids",
            description = "This operation retrieves all cat ids from the database",
            deprecated = false,
            hidden = false)
    public List<CatId> catids() {
        PanacheQuery<CatId> query = Cat.findAll().project(CatId.class);
        return query.list();
    }

    /**
     * Return a list of the top 3 cats by vote count
     * @return
     */
    @GET
    @Path("/topcats")
    @Operation(operationId = "topcats",
            summary = "get sorted list of top 3 cats by count descending",
            description = "This operation retrieves top 3 cats from the database sorted by count descending",
            deprecated = false,
            hidden = false)
    public List<Cat> topcats() {
        PanacheQuery<Cat> query = Cat.findAll(Sort.by("count").descending()).page(Page.ofSize(3));
        return query.list();
    }

    /**
     * Find cat by id
     * @param id
     * @return
     */
    @GET
    @Path("/{id}")
    @Operation(operationId = "getById",
            summary = "get cat by id",
            description = "This operation retrieves a cat by id from the database",
            deprecated = false,
            hidden = false)
    public Cat get(@PathParam("id") String id) {
        Cat ret = Cat.findById(new ObjectId(id));
        return ret;
    }

    /**
     * Create or Update a Cat
     * @param cat
     * @return
     */
    @POST
    @Operation(operationId = "createOrUpdate",
            summary = "create or update cat",
            description = "This operation creates or updates a cat (if id supplied)",
            deprecated = false,
            hidden = false)
    @APIResponses(
            value = {
                    @APIResponse(
                            responseCode = "400",
                            description = "Bad data",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Response.class))),
                    @APIResponse(
                            responseCode = "201",
                            description = "cat created or updated OK",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Response.class)))})
    @Metered(unit = MetricUnits.PER_SECOND, name = "cats-uploaded", description = "Frequency of cats uploaded")
    public synchronized Response create(Cat cat) {
        cat.vote();
        cat.resizeCat();
        cat.persistOrUpdate();
        nsfw(cat);
        if (!cat.getIssfw())
            return Response.status(400).build();
        return Response.status(201).entity(cat.id).build();
    }

    /**
     * Update a cat
     * @param cat
     */
    @PUT
    @Path("/")
    @Operation(operationId = "update",
            summary = "update cat",
            description = "This operation updates a cat (id supplied) - prefer POST method",
            deprecated = false,
            hidden = true)
    public void update(Cat cat) {
        cat.update();
    }

    /**
     * Delete a cat by id
     * @param id
     */
    @DELETE
    @Path("/{id}")
    @Operation(operationId = "delete",
            summary = "delete cat by id",
            description = "This operation deletes a cat by id",
            deprecated = false,
            hidden = false)
    public void delete(@PathParam("id") String id) {
        Cat cat = Cat.findById(new ObjectId(id));
        cat.delete();
    }

    /**
     * Count all cats
     * @return
     */
    @GET
    @Path("/count")
    @Operation(operationId = "count",
            summary = "count all cats",
            description = "This operation returns a count of all cats in the database",
            deprecated = false,
            hidden = false)
    public Long count() {
        return Cat.count();
    }

    /**
     * Delete all cats
     */
    @DELETE
    @Path("/kittykiller")
    @Operation(operationId = "kittykiller",
            summary = "⚡ remove all cats ⚡",
            description = "This operation deletes all cats from the database",
            deprecated = false,
            hidden = false)
    public void deleteAll() {
        Cat.deleteAll();
    }

    /**
     * Generate a datatable used in the default webpage for this app. Handy for viewing all images without the UI
     * @param draw
     * @param start
     * @param length
     * @param searchVal
     * @return
     */
    @GET
    @Path("/datatable")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "datatable",
            summary = "datatable cats by id",
            description = "This operation returns a datatable of cats - https://www.datatables.net/",
            deprecated = false,
            hidden = false)
    public DataTable datatable(
            @QueryParam(value = "draw") @DefaultValue("1") int draw,
            @QueryParam(value = "start") @DefaultValue("0") int start,
            @QueryParam(value = "length") @DefaultValue("10") int length,
            @QueryParam(value = "search[value]") String searchVal
    ) {
        // Begin result
        DataTable result = new DataTable();
        result.setDraw(draw);

        // Filter based on search
        PanacheQuery<Cat> filteredCats;

        // FIXME Search busted with ID
        /*if (searchVal != null && !searchVal.isEmpty()) {
            String s = "{\"_id\": ObjectId(\":search\")}";
            filteredCats = Cat.<Cat>find(s, Parameters.with("search", searchVal));
        } else {*/
        filteredCats = Cat.findAll();
        //}
        // Page and return
        int page_number = start / length;
        filteredCats.page(page_number, length);

        result.setRecordsFiltered(filteredCats.count());
        result.setData(filteredCats.list());
        result.setRecordsTotal(Cat.count());

        return result;
    }

    /**
     * Statically load images in resources folder at startup
     */
    @GET
    @Path("/loadlitter")
    @Operation(operationId = "loadlitter",
            summary = "preload db with cats",
            description = "This operation adds some cats to the database if it is empty",
            deprecated = false,
            hidden = false)
    public static void loadlitter() {
        if (Cat.count() > 0)
            return;
        final List<String> catList = Arrays.asList("cat1.jpeg", "cat2.jpeg", "cat3.jpeg", "cat4.jpeg", "cat5.jpeg", "cat6.jpeg", "cat7.jpeg", "cat8.jpeg", "cat9.jpeg", "cat10.jpeg", "cat11.jpeg", "cat12.jpeg", "dog1.jpeg");
        for (String tc : catList) {
            try {
                InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(tc);
                Cat cat = new Cat();
                cat.setCount(new Random().nextInt(5) + 1);
                cat.setVote(false);
                byte[] fileContent = new byte[0];
                fileContent = is.readAllBytes();
                String encodedString = Base64
                        .getEncoder()
                        .encodeToString(fileContent);
                cat.setImage("data:image/jpeg;base64," + encodedString);
                cat.resizeCat();
                cat.setIssfw(true);
                cat.persistOrUpdate();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Not Safe For Work client rest call
     * @param cat
     */
    private void nsfw(Cat cat) {
        try {
            TransactionRequest transactionRequest = new TransactionRequest(cat.id.toString(), cat.getImage());
            TransactionResponse transactionResponse = nsfwTransactionService.nsfw(transactionRequest);
            if (!transactionResponse.isIssfw()) {
                log.info("NSFW: " + transactionResponse);
                cat.setCount(-1000); // never in topcats
            }
            cat.setIssfw(transactionResponse.isIssfw());

        } catch (Exception e) {
            // no service found assuming image is safe for now
            cat.setIssfw(true);
        }
        // we need the cat.id for tracing nsfw calls, else we could have just done this once
        cat.persistOrUpdate();
    }
}
