package com.lucalzt.mctranslator.infrastructure.adapter.in;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.test.ShellAssertions;
import org.springframework.shell.test.ShellScreen;
import org.springframework.shell.test.ShellTestClient;
import org.springframework.shell.test.autoconfigure.ShellTest;
import org.springframework.boot.test.context.SpringBootTest;

@ShellTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
		properties = { "spring.shell.interactive.enabled=false" })
class TranslateCommandTests {

	@Autowired
	private ShellTestClient client;

	@Test
	@DisplayName("El comando translate se registra en el shell")
	void translateCommandIsRegistered() throws Exception {
		// when
		ShellScreen helpScreen = client.sendCommand("help");

		// then
		ShellAssertions.assertThat(helpScreen).containsText("modpack-translator");
	}

	@Test
	@DisplayName("translate responde sin implementación para la fase de bootstrap")
	void translateRespondsWithBootstrapMessage() throws Exception {
		// when
		ShellScreen screen = client.sendCommand("modpack-translator translate -f modpack.json");

		// then
		ShellAssertions.assertThat(screen).containsText("todavía no implementada");
	}

}
