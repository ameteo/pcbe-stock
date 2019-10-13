package pcbe.stock;

import static java.lang.Math.random;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import pcbe.stock.client.StockClient;
import pcbe.stock.server.StockServer;

public class SystemInitializer {
    private static final Random random = new Random();

    public static void initializeSystem(StockServer server, Collection<StockClient> clients) {
        var allCompanies = Arrays.asList(
            "Apple Inc.", "Samsung Electronics", "Amazon", "Foxconn",
            "Alphabet Inc.", "Microsoft", "Huawei", "Hitachi", 
            "IBM", "Dell Technologies", "Sony", "Panasonic", 
            "Intel", "LG Electronics", "JD.com", "HP Inc.");
        var selectedCompanies = allCompanies.stream()
            .filter(c -> shouldSelectCompany())
            .collect(toList());
        var currentShares = selectedCompanies.stream()
            .collect(toMap(c -> c, c -> getRandomShares()));
        server.addCompanies(selectedCompanies);
        for (var client : clients) {
            client.addCurrencyUnits(getCurrencyAmount());
            for (var company : selectedCompanies)
                if (shouldGiveSharesToClient()) {
                    var availableShares = currentShares.get(company);
                    var givenShares = getSharesLessThan(availableShares);
                    client.addShares(company, givenShares);
                    currentShares.put(company, availableShares - givenShares);
                } else
                    client.addShares(company, 0);
        }
    }

    private static Integer getCurrencyAmount() {
        return 1000 * (random.nextInt(5) + 1);
    }

    private static boolean shouldSelectCompany() {
        return random() > .5;
    }

    private static boolean shouldGiveSharesToClient() {
        return random() > .2;
    }

    private static int getRandomShares() {
        return 50 * (random.nextInt(10) + 10);
    }

    private static int getSharesLessThan(int amount) {
        return 50 * random.nextInt(amount / 50);
    }
}