package pcbe.stock;

import static java.lang.Math.random;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
        var selectedCompanies = selectCompanies(allCompanies);
        var sharesPerCompany = initializeSharesPerCompany(selectedCompanies);
        provideClientsWithCurrencyUnits(clients);
        provideClientsWithShares(selectedCompanies, sharesPerCompany, clients);
    }

    private static void provideClientsWithShares(List<String> selectedCompanies, Map<String, Integer> sharesPerCompany, Collection<StockClient> clients) {
        for (var company : selectedCompanies) {
            var client = getRandomClient(clients);
            client.addShares(company, sharesPerCompany.get(company));
        }
    }

    private static Map<String, Integer> initializeSharesPerCompany(List<String> selectedCompanies) {
        return selectedCompanies.stream()
            .collect(toMap(c -> c, c -> getRandomShares()));
    }

    private static List<String> selectCompanies(List<String> allCompanies) {
        return allCompanies.stream()
            .filter(c -> shouldSelectCompany())
            .collect(toList());
    }

    private static void provideClientsWithCurrencyUnits(Collection<StockClient> clients) {
        for (var client : clients)
            client.addCurrencyUnits(getCurrencyAmount());
    }

    private static StockClient getRandomClient(Collection<StockClient> clients) {
        return clients.stream().findAny().get();
    }

    private static Integer getCurrencyAmount() {
        return 1000 * (random.nextInt(5) + 1);
    }

    private static boolean shouldSelectCompany() {
        return random() > .5;
    }

    private static int getRandomShares() {
        return 50 * (random.nextInt(10) + 10);
    }
}