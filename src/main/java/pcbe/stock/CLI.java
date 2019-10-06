
package pcbe.stock;

import java.net.URI;
import pcbe.stock.server.StockServer;

public class CLI {
    private static final URI SERVER_URI = URI.create("http://localhost:8080/stock/");

    public static void main(String[] args) throws IOException {
        var stockServer = new StockServer(SERVER_URI);
        logger.info("ENTER to stop the program.");
        while (System.in.read() != '\n');
        stockServer.shutdownNow();
    }
}
