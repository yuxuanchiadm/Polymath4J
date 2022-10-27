package org.yuxuan.polymath4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class PackCleaner implements Runnable {
	public final Polymath4J polymath4J;
	public final Logger logger;
	public final ScheduledExecutorService scheduledExecutorService;
	public final PackManager packManager;

	public PackCleaner(Polymath4J polymath4J) {
		this.polymath4J = polymath4J;
		this.logger = polymath4J.logger;
		this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		this.packManager = polymath4J.packManager;
	}

	public boolean start() {
		scheduledExecutorService.scheduleAtFixedRate(this, 0, polymath4J.delay, TimeUnit.SECONDS);
		return true;
	}

	public void run() {
		packManager.clean();
	}

	public void stop() {
		scheduledExecutorService.shutdown();
	}
}
