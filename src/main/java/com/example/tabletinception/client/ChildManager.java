package com.example.tabletinception.client;

import com.example.tabletinception.TabletConfig;
import com.example.tabletinception.TabletInceptionMod;
import com.example.tabletinception.channel.Channel;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

/** Inicia, acompanha e encerra o Minecraft interno (processo filho). */
public final class ChildManager {
    public enum Status { OFF, STARTING, RUNNING, DEAD }

    private static Process process;
    private static Channel channel;
    private static ByteBuffer frameBuffer;
    private static String lastError = "";
    private static boolean hookInstalled;

    private ChildManager() {}

    public static synchronized Status status() {
        if (process == null) return Status.OFF;
        if (!process.isAlive()) return Status.DEAD;
        return channel != null && channel.state() >= 2 ? Status.RUNNING : Status.STARTING;
    }

    public static synchronized Channel channel() { return channel; }

    public static synchronized String lastError() { return lastError; }

    public static synchronized ByteBuffer frameBuffer() {
        if (frameBuffer == null) {
            frameBuffer = ByteBuffer.allocateDirect(Channel.FRAME_CAP).order(ByteOrder.nativeOrder());
        }
        return frameBuffer;
    }

    public static synchronized Path logFile() {
        return FabricLoader.getInstance().getGameDir().resolve("tablet_inception").resolve("child.log");
    }

    public static synchronized void start(int width, int height) {
        Status st = status();
        if (st == Status.STARTING || st == Status.RUNNING) return;
        lastError = "";
        try {
            doStart(width, height);
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            TabletInceptionMod.LOGGER.error("Falha ao iniciar o Minecraft interno", e);
        }
    }

    /** Pede para o jogo interno fechar; forca o encerramento se demorar. */
    public static synchronized void stop() {
        if (process == null || !process.isAlive()) return;
        if (channel != null) channel.requestShutdown();
        final Process p = process;
        Thread t = new Thread(() -> {
            try {
                if (!p.waitFor(12, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                p.destroyForcibly();
            }
        }, "tablet-inception-stop");
        t.setDaemon(true);
        t.start();
    }

    private static void doStart(int width, int height) throws IOException {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            throw new IOException("Ambiente de desenvolvimento nao suportado: compile o .jar e teste em um launcher.");
        }
        TabletConfig cfg = TabletConfig.load();
        Path root = FabricLoader.getInstance().getGameDir().resolve("tablet_inception");
        Path instance = root.resolve("instance");
        Path mods = instance.resolve("mods");
        Files.createDirectories(mods);

        // Mods do jogo interno: este mod + Fabric API (copiados do jogo atual).
        List<Path> jars = new ArrayList<>();
        jars.addAll(modJars("tablet_inception"));
        jars.addAll(modJars("fabric-api"));
        if (jars.size() < 2) {
            throw new IOException("Nao encontrei os .jar do Tablet Inception e do Fabric API.");
        }
        Set<String> keep = new HashSet<>();
        for (Path jar : jars) {
            Path target = mods.resolve(jar.getFileName().toString());
            keep.add(target.getFileName().toString());
            Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
        }
        try (Stream<Path> s = Files.list(mods)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isRegularFile(p) && !keep.contains(p.getFileName().toString())) {
                    Files.deleteIfExists(p);
                }
            }
        }

        if (channel == null) {
            channel = Channel.create(root.resolve("channel.bin"));
        } else {
            channel.reset();
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBinary(cfg));
        for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (!skipJvmArg(a)) cmd.add(a);
        }
        cmd.add("-Xmx" + cfg.memoryMb + "M");
        cmd.add("-Dtablet_inception.child=" + root.resolve("channel.bin"));
        cmd.add("-Dtablet_inception.parentPid=" + ProcessHandle.current().pid());
        cmd.add("-Dtablet_inception.fps=" + cfg.fps);
        if (!cfg.extraJvmArgs.isBlank()) {
            cmd.addAll(Arrays.asList(cfg.extraJvmArgs.trim().split("\\s+")));
        }
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(mainClass());
        cmd.addAll(gameArgs(cfg, instance, width, height));

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(instance.toFile())
                .redirectErrorStream(true)
                .redirectOutput(root.resolve("child.log").toFile());
        process = pb.start();
        installHook();
        TabletInceptionMod.LOGGER.info("Minecraft interno iniciado (pid {}). Log: {}", process.pid(), logFile());
    }

    private static void installHook() {
        if (hookInstalled) return;
        hookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = process;
            if (p != null && p.isAlive()) p.destroyForcibly();
        }, "tablet-inception-kill"));
    }

    private static String javaBinary(TabletConfig cfg) {
        if (!cfg.javaPath.isBlank()) return cfg.javaPath;
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", win ? "java.exe" : "java").toString();
    }

    private static String mainClass() {
        String cmd = System.getProperty("sun.java.command", "");
        String first = cmd.isBlank() ? "" : cmd.trim().split(" ")[0];
        if (first.contains("KnotClient") || first.toLowerCase(Locale.ROOT).contains("devlaunchinjector")) {
            return first;
        }
        return "net.fabricmc.loader.impl.launch.knot.KnotClient";
    }

    private static boolean skipJvmArg(String a) {
        return a.startsWith("-Xmx") || a.startsWith("-Xms")
                || a.startsWith("-agentlib") || a.startsWith("-Xrunjdwp")
                || a.startsWith("-Djava.class.path") || a.equals("-cp") || a.equals("-classpath")
                || a.startsWith("-Dtablet_inception") || a.startsWith("-Dfabric.addMods");
    }

    private static List<Path> modJars(String id) throws IOException {
        Optional<ModContainer> c = FabricLoader.getInstance().getModContainer(id);
        if (c.isEmpty()) throw new IOException("Mod nao encontrado: " + id);
        ModOrigin origin = c.get().getOrigin();
        if (origin.getKind() != ModOrigin.Kind.PATH) {
            throw new IOException("Origem inesperada para o mod " + id);
        }
        List<Path> out = new ArrayList<>();
        for (Path p : origin.getPaths()) {
            if (!Files.isRegularFile(p)) {
                throw new IOException("O mod " + id + " precisa estar em um .jar (nao em pasta).");
            }
            out.add(p);
        }
        return out;
    }

    private static List<String> gameArgs(TabletConfig cfg, Path instance, int width, int height) {
        String[] raw = FabricLoader.getInstance().getLaunchArguments(false);
        Set<String> dropKeys = Set.of("--xuid", "--clientId", "--server", "--port",
                "--quickPlayPath", "--quickPlaySingleplayer", "--quickPlayMultiplayer", "--quickPlayRealms");
        Set<String> dropFlags = Set.of("--fullscreen", "--demo");

        Map<String, String> kv = new LinkedHashMap<>();
        List<String> flags = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            String t = raw[i];
            if (!t.startsWith("--") || dropFlags.contains(t)) continue;
            boolean hasValue = i + 1 < raw.length && !raw[i + 1].startsWith("--");
            if (hasValue) {
                if (!dropKeys.contains(t)) kv.put(t, raw[i + 1]);
                i++;
            } else if (!dropKeys.contains(t)) {
                flags.add(t);
            }
        }
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + cfg.username).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        kv.put("--gameDir", instance.toString());
        kv.put("--username", cfg.username);
        kv.put("--uuid", uuid);
        kv.put("--accessToken", "0");
        kv.put("--userType", "legacy");
        kv.put("--width", String.valueOf(width));
        kv.put("--height", String.valueOf(height));

        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : kv.entrySet()) {
            out.add(e.getKey());
            out.add(e.getValue());
        }
        out.addAll(flags);
        return out;
    }
}
