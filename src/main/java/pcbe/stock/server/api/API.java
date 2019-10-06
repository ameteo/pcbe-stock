package pcbe.stock.server.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pcbe.stock.model.request.RegisterRequest;


@Path("/")
public class API {
    public static final String REGISTER = "register";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private StockService stockService = StockService.getDefault();

    @Path(REGISTER)
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String register(String json) {
        var registerRequest = gson.fromJson(json, RegisterRequest.class);
        var registerResponse = stockService.register(registerRequest);
        return gson.toJson(registerResponse);
    }
}