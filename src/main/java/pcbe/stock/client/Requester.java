package pcbe.stock.client;

import static javax.ws.rs.client.Entity.json;

import java.net.URI;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import pcbe.stock.model.request.Request;
import pcbe.stock.model.response.Response;

public class Requester {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private WebTarget webTarget;

    public Requester(URI uri) {
        this.webTarget = ClientBuilder.newClient().target(uri);
    }

    public <T extends Response> T request(Request<T> request) {
        var response = webTarget.path(request.getPath()).request().post(json(gson.toJson(request)));
        return gson.fromJson(response.readEntity(String.class), request.getResultClass());
    }
}
