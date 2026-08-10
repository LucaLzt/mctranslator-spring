package com.lucalzt.mctranslator.infrastructure.adapter.out.nllb;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the fast NLLB-200 ONNX translation engine.
 *
 * <p>Bound to the {@code mctranslator.engine.nllb} prefix. The properties define the directory
 * holding the local model assets (tokenizer and ONNX graphs) and the maximum number of tokens the
 * autoregressive decoder may generate per translation.
 */
@Component
@ConfigurationProperties(prefix = "mctranslator.engine.nllb")
@NullMarked
public class NllbEngineProperties {

	private String modelDir = "models/nllb";

	private int maxNewTokens = 128;

	/**
	 * Returns the configured model directory string.
	 *
	 * @return the model directory path
	 */
	public String getModelDir() {
		return modelDir;
	}

	/**
	 * Sets the model directory string.
	 *
	 * @param modelDir the model directory path to set
	 */
	public void setModelDir(String modelDir) {
		this.modelDir = modelDir;
	}

	/**
	 * Returns the configured maximum number of generated tokens per translation.
	 *
	 * @return the maximum number of new tokens
	 */
	public int getMaxNewTokens() {
		return maxNewTokens;
	}

	/**
	 * Sets the maximum number of generated tokens per translation.
	 *
	 * @param maxNewTokens the maximum number of new tokens to set
	 */
	public void setMaxNewTokens(int maxNewTokens) {
		this.maxNewTokens = maxNewTokens;
	}

	/**
	 * Resolves the model directory relative to the working directory.
	 *
	 * @return the resolved absolute or normalized path
	 */
	public Path resolveModelDir() {
		Path path = Paths.get(modelDir);
		if (!path.isAbsolute()) {
			return Paths.get(".").resolve(path).normalize();
		}
		return path;
	}
}
