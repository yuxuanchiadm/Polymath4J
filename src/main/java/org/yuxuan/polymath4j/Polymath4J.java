package org.yuxuan.polymath4j;

import com.moandjiezana.toml.Toml;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.log.Log;

public final class Polymath4J implements Runnable, Closeable {
	public final Logger logger;

	public int port;
	public String url;
	public long maxSize;
	public long delay;
	public long packLifespan;

	public PackServer packServer;
	public PackCleaner packCleaner;
	public PackManager packManager;

	public Polymath4J(Logger logger) {
		this.logger = logger;
	}

	@Override
	public void run() {
		logger.log(Level.INFO, "Starting Polymath4J...");
		if (!initConfig()) return;
		packManager = new PackManager(this);
		packServer = new PackServer(this);
		packCleaner = new PackCleaner(this);
		if (!packManager.start()) return;
		if (!packServer.start()) return;
		if (!packCleaner.start()) return;
		logger.log(Level.INFO, "Polymath4J successfully started");
	}

	@Override
	public void close() {
		logger.log(Level.INFO, "Stopping Polymath4J...");
		packCleaner.stop();
		packServer.stop();
		packManager.stop();
		logger.log(Level.INFO, "Polymath4J successfully stopped");
	}

	private boolean initConfig() {
		File settingsFile = new File("settings.toml");
		if (!settingsFile.exists()) {
			File parent = settingsFile.getAbsoluteFile().getParentFile();
			if (!parent.exists() && !parent.mkdirs()) {
				logger.log(Level.SEVERE, "Cannot create settings parent directory!");
				return false;
			}
			try (
				InputStream inputStream = Polymath4J.class.getClassLoader().getResourceAsStream("settings.toml");
				OutputStream outputStream = Files.newOutputStream(settingsFile.toPath())
			) {
				if (inputStream == null) {
					logger.log(Level.SEVERE, "Missing default settings!");
					return false;
				}
				IOUtils.copy(inputStream, outputStream);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Cannot copy default settings!", e);
				return false;
			}
		}
		Toml toml = new Toml();
		toml.read(settingsFile);
		Toml serverSettings = toml.getTable("server");
		if (serverSettings == null) {
			logger.log(Level.SEVERE, "Settings missing server!");
			return false;
		}
		String portStr = serverSettings.getString("port");
		if (portStr == null) {
			logger.log(Level.SEVERE, "Settings missing server.port!");
			return false;
		}
		int port;
		try {
			port = Integer.parseInt(portStr);
		} catch (NumberFormatException e) {
			logger.log(Level.SEVERE, "Settings server.port must be number!");
			return false;
		}
		if (port < 0 || port > 65535) {
			logger.log(Level.SEVERE, "Settings server.port must between 0 and 65535!");
			return false;
		}
		String url = serverSettings.getString("url");
		if (url == null) {
			logger.log(Level.SEVERE, "Settings missing server.url!");
			return false;
		}
		Toml requestSettings = toml.getTable("request");
		if (requestSettings == null) {
			logger.log(Level.SEVERE, "Settings missing request!");
			return false;
		}
		Long maxSize = requestSettings.getLong("max_size");
		if (maxSize == null || maxSize < 0) {
			logger.log(Level.SEVERE, "Settings missing request.max_size!");
			return false;
		}
		Toml cleanerSettings = toml.getTable("cleaner");
		if (cleanerSettings == null) {
			logger.log(Level.SEVERE, "Settings missing cleaner!");
			return false;
		}
		Long delay = cleanerSettings.getLong("delay");
		if (delay == null || delay < 0) {
			logger.log(Level.SEVERE, "Settings missing cleaner.delay!");
			return false;
		}
		Long packLifespan = cleanerSettings.getLong("pack_lifespan");
		if (packLifespan == null || packLifespan < 0) {
			logger.log(Level.SEVERE, "Settings missing cleaner.packLifespan!");
			return false;
		}
		this.port = port;
		this.url = url;
		this.maxSize = maxSize;
		this.delay = delay;
		this.packLifespan = packLifespan;
		return true;
	}

	public static void main(String[] args) {
		Log.setLog(new NoLog());
		Logger logger = Logger.getLogger("Polymath4J");
		try {
			FileHandler fileHandler = new FileHandler("polymath4j.log");
			fileHandler.setFormatter(new SimpleFormatter());
			logger.addHandler(fileHandler);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		Polymath4J polymath4J = new Polymath4J(logger);
		polymath4J.run();
	}
}

class NoLog implements org.eclipse.jetty.util.log.Logger {
	@Override public String getName() { return "NoLog"; }
	@Override public void warn(String msg, Object... args) { }
	@Override public void warn(Throwable thrown) { }
	@Override public void warn(String msg, Throwable thrown) { }
	@Override public void info(String msg, Object... args) { }
	@Override public void info(Throwable thrown) { }
	@Override public void info(String msg, Throwable thrown) { }
	@Override public boolean isDebugEnabled() { return false; }
	@Override public void setDebugEnabled(boolean enabled) { }
	@Override public void debug(String msg, Object... args) { }
	@Override public void debug(String msg, long value) { }
	@Override public void debug(Throwable thrown) { }
	@Override public void debug(String msg, Throwable thrown) { }
	@Override public org.eclipse.jetty.util.log.Logger getLogger(String name) { return this; }
	@Override public void ignore(Throwable ignored) { }
}
