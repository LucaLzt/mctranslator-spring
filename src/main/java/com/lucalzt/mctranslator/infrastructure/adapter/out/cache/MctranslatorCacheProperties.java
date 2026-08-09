package com.lucalzt.mctranslator.infrastructure.adapter.out.cache;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the SQLite translation cache.
 */
@Component
@ConfigurationProperties(prefix = "mctranslator.cache")
@NullMarked
public class MctranslatorCacheProperties {

	private String dbPath = "./mctranslator.db";

	/**
	 * Returns the configured database path string.
	 *
	 * @return the database path
	 */
	public String getDbPath() {
		return dbPath;
	}

	/**
	 * Sets the database path string.
	 *
	 * @param dbPath the database path to set
	 */
	public void setDbPath(String dbPath) {
		this.dbPath = dbPath;
	}

	/**
	 * Resolves the database path relative to the working directory.
	 *
	 * @return the resolved absolute or normalized path
	 */
	public Path resolveDbPath() {
		Path path = Paths.get(dbPath);
		if (!path.isAbsolute()) {
			return Paths.get(".").resolve(path).normalize();
		}
		return path;
	}
}
