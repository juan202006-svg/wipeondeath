package com.wipeondeath;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class WipeOnDeath implements ModInitializer {

	private static final String FLAG_NAME = "wipeondeath.flag";
	private static final AtomicBoolean TRIGGERED = new AtomicBoolean(false);

	private static MinecraftServer server;
	private static boolean onlyHardcore = false;

	@Override
	public void onInitialize() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path configDir = FabricLoader.getInstance().getConfigDir();

		loadConfig(configDir);

		Path flag = gameDir.resolve(FLAG_NAME);
		if (Files.exists(flag)) {
			String levelName = readLevelName(gameDir);
			Path world = gameDir.resolve(levelName);
			System.out.println("[WipeOnDeath] Marcador encontrado. Borrando mundo: " + world);
			try {
				deleteRecursively(world);
				Files.deleteIfExists(flag);
				System.out.println("[WipeOnDeath] Mundo borrado. Se generara uno nuevo.");
			} catch (IOException e) {
				System.err.println("[WipeOnDeath] No pude borrar el mundo: " + e);
			}
		}

		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) return;
			if (server == null) return;
			if (onlyHardcore && !server.isHardcore()) return;
			if (!TRIGGERED.compareAndSet(false, true)) return;

			try {
				Files.createFile(FabricLoader.getInstance().getGameDir().resolve(FLAG_NAME));
			} catch (IOException e) {
				// si ya existe, no pasa nada
			}

			server.getPlayerList().broadcastSystemMessage(
				Component.literal("§c" + player.getName().getString()
					+ " murio. El mundo se reiniciara. Vuelve a encender el servidor."),
				false
			);
			System.out.println("[WipeOnDeath] " + player.getName().getString() + " murio. Apagando servidor.");
			server.halt(false);
		});
	}

	private static void loadConfig(Path configDir) {
		Path file = configDir.resolve("wipeondeath.properties");
		Properties props = new Properties();
		try {
			if (Files.exists(file)) {
				try (InputStream in = Files.newInputStream(file)) {
					props.load(in);
				}
			} else {
				Files.createDirectories(configDir);
				props.setProperty("only_hardcore", "false");
				try (OutputStream out = Files.newOutputStream(file)) {
					props.store(out, "only_hardcore=false: cualquier modo | true: solo si el servidor es hardcore");
				}
			}
		} catch (IOException e) {
			System.err.println("[WipeOnDeath] Error con la config: " + e);
		}
		onlyHardcore = Boolean.parseBoolean(props.getProperty("only_hardcore", "false").trim());
	}

	private static String readLevelName(Path gameDir) {
		Path propsFile = gameDir.resolve("server.properties");
		Properties props = new Properties();
		if (Files.exists(propsFile)) {
			try (InputStream in = Files.newInputStream(propsFile)) {
				props.load(in);
			} catch (IOException ignored) {
			}
		}
		return props.getProperty("level-name", "world").trim();
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
				Files.deleteIfExists(p);
			}
		}
	}
}
