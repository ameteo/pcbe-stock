package pcbe.stock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Executor {
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static ExecutorService getDefault() {
        return executor;
    }
}