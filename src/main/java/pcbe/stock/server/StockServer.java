package pcbe.stock.server;

import java.net.URI;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import pcbe.stock.server.api.API;


public class StockServer {
    public static final String API_PACKAGE = API.class.getPackageName();
    private final HttpServer httpServer;

    public StockServer(URI uri) {
        httpServer = GrizzlyHttpServerFactory.createHttpServer(uri, new ResourceConfig().packages(API_PACKAGE));
    }

    public void shutdownNow() {
        httpServer.shutdownNow();
    }

}
