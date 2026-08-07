package com.lucalzt.mctranslator.infrastructure.adapter.in;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.CommandGroup;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

/**
 * Comando raíz del CLI que orquesta la traducción de un modpack de Minecraft.
 *
 * <p>Este es el bootstrap inicial de Spring Shell 4.0: registra el comando
 * {@code translate} para validar el arranque del shell. La lógica real de
 * traducción se incorpora en iteraciones posteriores.
 */
@Component
@CommandGroup(prefix = "modpack-translator", name = "Modpack Translator Commands")
public class TranslateCommand {

	/**
	 * Inicia la traducción de un modpack desde un archivo JSON.
	 *
	 * @param file ruta al archivo del modpack (formato JSON)
	 * @return mensaje de estado de la operación
	 */
	@Command(name = "translate", description = "Traduce un modpack de Minecraft de forma local")
	public String translate(
			@Option(shortName = 'f', longName = "file", required = true,
					description = "Ruta al archivo del modpack (JSON)") String file) {
		return "Traducción de '%s' todavía no implementada (bootstrap Spring Shell 4.0).".formatted(file);
	}

}
