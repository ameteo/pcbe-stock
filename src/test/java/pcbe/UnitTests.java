package pcbe;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.RepeatedTest.LONG_DISPLAY_NAME;
import static org.junit.platform.commons.util.ReflectionUtils.tryToReadFieldValue;

import java.util.Collection;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import pcbe.stock.client.StockClient;
import pcbe.stock.client.StockClientGenerator;
import pcbe.stock.server.StockServer;
import pcbe.stock.server.StockService;

/**
 * This class holds all the unit tests of the application.
 */
class UnitTests {

    private static final int LIFESPAN = 120;

    @Test
    void cannotInstantiateInvalidClient() {
        assertThrows(RuntimeException.class, () -> new StockClient(null, 0));
    }

    @Test
    void canInstantiateValidClient() {
        assertDoesNotThrow(() -> new StockClient(UUID.randomUUID(), LIFESPAN));
    }

    @Test
    void canCreateServer() {
        assertDoesNotThrow(() -> new StockServer());
    }

    @RepeatedTest(name = LONG_DISPLAY_NAME, value = 10)
    void canGenerateFiveToTenClients() {
        assertThat(StockClientGenerator.generateClients(), hasSize(both(not(lessThan(5))).and(lessThanOrEqualTo(10))));
    }

    @Nested
    class OneClient {

        StockClient client;

        @BeforeEach
        void createNewClient() {
            client = new StockClient(UUID.randomUUID(), LIFESPAN);
        }

        @Test
        void clientCannotRegisterToInvalidServer() {
            assertThrows(RuntimeException.class, () -> client.registerTo(null));
        }

        @Test
        public void clientCanOnlyRegisterOnce() {
            var server = new StockServer();
            assertDoesNotThrow(() -> client.registerTo(server));
            assertThrows(RuntimeException.class, () -> client.registerTo(server));
        }
    }

    @Nested
    class MultipleClientsWithoutConcurrency {
        @Test
        @Disabled("temporary")
        public void multipleClientsCanRegister() {
            var server = new StockServer();
            var clients = StockClientGenerator.generateClients();
            assertDoesNotThrow(() -> clients.forEach(client -> client.registerTo(server)));
            tryToReadFieldValue(StockService.class, "clientIds", StockService.getDefault())
                .ifFailure(Assertions::fail)
                .andThenTry(Collection.class::cast)
                .ifSuccess(clientIds -> 
                    clients.stream()
                    .forEach(client -> 
                        tryToReadFieldValue(StockClient.class, "id", client)
                        .ifFailure(Assertions::fail)
                        .andThenTry(UUID.class::cast)
                        .ifSuccess(clientId -> assertTrue(clientIds.contains(clientId)))
                    )
                );
        }
    }

    @Nested
    class MultipleClientsWithConcurrency {

    }

}
