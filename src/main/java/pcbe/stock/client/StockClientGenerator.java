package pcbe.stock.client;

import static java.lang.Math.random;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class StockClientGenerator {
    public static Collection<StockClient> generateClients() {
        var minCount = new AtomicInteger(5);
        return Stream.generate(UUID::randomUUID).map(UUID::toString).map(StockClient::new)
                .takeWhile(c -> minCount.decrementAndGet() > 0 || random() > .1).limit(10).collect(toList());
    }
}
