package com.lucalzt.mctranslator;

import com.lucalzt.mctranslator.infrastructure.adapter.out.nllb.FastNllbAdapter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Context-loading smoke test.
 *
 * <p>{@link FastNllbAdapter} is mocked so the Spring context does not load the local ONNX model
 * assets (roughly 850 MB) during the regular test run; the real engine is exercised by
 * {@code FastNllbAdapterIntegrationTest} when {@code -Dmctranslator.it.nllb=true} is set.
 */
@SpringBootTest
class MctranslatorApplicationTests {

	@MockitoBean
	FastNllbAdapter fastNllbAdapter;

	@Test
	void contextLoads() {
	}
}
