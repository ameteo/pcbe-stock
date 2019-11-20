package pcbe.stock.client;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

public class StockClientGenerator {
    /**
     * @return a collection of 5 to 10 clients with a random UUID
     */
    public static Collection<StockClient> generateClients() {
        return Stream.generate(UUID::randomUUID).limit(new Random().nextInt(5) + 5)
                .map(id -> new StockClient(id, 5 + new Random().nextInt(15))).collect(toList());
    }
}
