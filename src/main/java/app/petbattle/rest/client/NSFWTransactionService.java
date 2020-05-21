package app.petbattle.rest.client;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("/api")
@RegisterRestClient
public interface NSFWTransactionService {

    @POST
    @Path("/v1.0/nsfw")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    TransactionResponse nsfw(TransactionRequest request);
}
