package org.yuxuan.polymath4j;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

public final class PackManager {
	public final Polymath4J polymath4J;
	public final Logger logger;
	public final Gson gson;
	public final File storageDirectory;
	public final File packsDirectory;
	public final File registryFile;
	public final Map<String, Entry> registry;

	public PackManager(Polymath4J polymath4J) {
		this.polymath4J = polymath4J;
		this.logger = polymath4J.logger;
		this.gson = new GsonBuilder().create();
		this.storageDirectory = new File("storage");
		this.packsDirectory = new File(storageDirectory, "packs");
		this.registryFile = new File(storageDirectory, "registry.json");
		this.registry = new HashMap<>();
	}

	public synchronized boolean start() {
		if (!storageDirectory.exists()) {
			if (!storageDirectory.mkdirs()) {
				logger.log(Level.SEVERE, "Cannot create storage directory!");
				return false;
			}
		} else if (!storageDirectory.isDirectory()) {
			logger.log(Level.SEVERE, "Storage is not directory!");
			return false;
		}
		if (!packsDirectory.exists()) {
			if (!packsDirectory.mkdirs()) {
				logger.log(Level.SEVERE, "Cannot create packs directory!");
				return false;
			}
		} else if (!packsDirectory.isDirectory()) {
			logger.log(Level.SEVERE, "Packs is not directory!");
			return false;
		}
		if (!registryFile.exists()) {
			saveRegistry();
		} else {
			if (!registryFile.isFile()) {
				logger.log(Level.SEVERE, "registry.json is not file!");
				return false;
			}
			loadRegistry();
		}
		return true;
	}

	public synchronized Optional<String> register(byte[] pack, String spigotId, String ip) {
		String idHash = DigestUtils.sha1Hex(pack);
		File file = new File(packsDirectory, idHash);
		try {
			FileUtils.writeByteArrayToFile(file, pack);
		} catch (IOException e) {
			logger.log(Level.WARNING, "Cannot write pack to file! idHash = " + idHash + ", spigotId = " + spigotId + ", ip = " + ip, e);
			return Optional.empty();
		}
		Entry entry = new Entry(spigotId, ip, System.currentTimeMillis() / 1000);
		registry.put(idHash, entry);
		saveRegistry();
		return Optional.of(idHash);
	}

	public synchronized Optional<File> fetch(String idHash) {
		Entry entry = registry.get(idHash);
		if (entry == null) {
			return Optional.empty();
		}
		entry.lastDownload = System.currentTimeMillis() / 1000;
		saveRegistry();
		File file = new File(packsDirectory, idHash);
		return Optional.of(file);
	}

	public synchronized void clean() {
		boolean changed = false;
		Iterator<Map.Entry<String, Entry>> iterator = registry.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Entry> mapEntry = iterator.next();
			String idHash = mapEntry.getKey();
			Entry entry = mapEntry.getValue();
			File file = new File(packsDirectory, idHash);
			long currentTime = System.currentTimeMillis() / 1000;
			if (!file.exists()) {
				iterator.remove();
				changed = true;
			} else if (currentTime - entry.lastDownload > polymath4J.packLifespan) {
				iterator.remove();
				changed = true;
				if (!file.delete()) {
					logger.log(Level.WARNING, "Cannot delete pack! idHash = " + idHash);
				}
			}
		}
		File[] files = packsDirectory.listFiles();
		if (files != null) {
			for (File file : files) {
				String idHash = file.getName();
				if (file.isFile() && !registry.containsKey(idHash)) {
					if (!file.delete()) {
						logger.log(Level.WARNING, "Cannot delete pack! idHash = " + idHash);
					}
				}
			}
		}
		if (changed) saveRegistry();
	}

	public synchronized void stop() {
		registry.clear();
		saveRegistry();
	}

	public synchronized void saveRegistry() {
		try (Writer writer = new OutputStreamWriter(Files.newOutputStream(registryFile.toPath()), StandardCharsets.UTF_8)) {
			gson.toJson(registry, new TypeToken<Map<String, Entry>>() {}.getType(), writer);
		} catch (IOException e) {
			logger.log(Level.WARNING, "Cannot save registry.json!", e);
		}
	}

	public synchronized void loadRegistry() {
		registry.clear();
		try (Reader reader = new InputStreamReader(Files.newInputStream(registryFile.toPath()), StandardCharsets.UTF_8)) {
			registry.putAll(gson.fromJson(reader, new TypeToken<Map<String, Entry>>() {}));
		} catch (IOException e) {
			logger.log(Level.WARNING, "Cannot load registry.json!", e);
		}
	}

	private static final class Entry {
		@SerializedName("id")
		public String id;
		@SerializedName("ip")
		public String ip;
		@SerializedName("last_download")
		public long lastDownload;

		public Entry() {

		}

		public Entry(String id, String ip, long lastDownload) {
			this.id = id;
			this.ip = ip;
			this.lastDownload = lastDownload;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getIp() {
			return ip;
		}

		public void setIp(String ip) {
			this.ip = ip;
		}

		public long getLastDownload() {
			return lastDownload;
		}

		public void setLastDownload(long lastDownload) {
			this.lastDownload = lastDownload;
		}
	}
}