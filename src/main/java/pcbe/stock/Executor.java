package pcbe.stock;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Executor {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static List<Timer> timers = new ArrayList<>();

    public static ExecutorService getDefault() {
        return executor;
    }

	public static Timer newTimer() {
        var timer = new Timer();
        timers.add(timer);
		return timer;
    }
    
    public static void cancelTimers() {
        timers.forEach(Timer::cancel);
    }
}