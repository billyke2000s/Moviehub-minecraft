package com.billyke2000s.moviehubtv.catalogue;

import com.billyke2000s.moviehubtv.ServerConfig;
import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Executes the exact bundled MovieHub engine without exposing a public service. */
public final class MovieHubEngine {
    private MovieHubEngine() {}
    private static final Set<Process> RUNNING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Process> OWNERS = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MovieHubTV-ResolverTimeout"); t.setDaemon(true); return t;
    });
    private static Path root;
    private static String python;
    private static synchronized void prepare() throws Exception {
        if (root != null && python != null) return;
        Path dir = FMLPaths.CONFIGDIR.get().resolve("moviehubtv-engine-0.10.0").toAbsolutePath();
        Files.createDirectories(dir);
        if (!Files.exists(dir.resolve("ready"))) {
            try (InputStream resource = MovieHubEngine.class.getResourceAsStream("/moviehub-engine.zip")) {
                if (resource == null) throw new IOException("This jar is missing MovieHub's provider engine");
                try (ZipInputStream zip = new ZipInputStream(resource)) {
                    ZipEntry entry; long total = 0;
                    while ((entry = zip.getNextEntry()) != null) {
                        Path target = dir.resolve(entry.getName()).normalize();
                        if (!target.startsWith(dir)) throw new IOException("Invalid engine path");
                        if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                        Files.createDirectories(target.getParent());
                        try (OutputStream out = Files.newOutputStream(target)) {
                            byte[] buf = new byte[16384]; int n;
                            while ((n = zip.read(buf)) != -1) {
                                total += n; if (total > 40000000) throw new IOException("Engine size limit");
                                out.write(buf, 0, n);
                            }
                        }
                    }
                }
            }
            Files.writeString(dir.resolve("ready"), "MovieHub 08343545afd7b535f8c9112f818ad60ee4432bd4");
        }
        String configured = ServerConfig.get(ServerConfig.PYTHON, "").trim();
        List<String> candidates = configured.isEmpty() ? List.of("python3", "python") : List.of(configured);
        String found = null;
        for (String candidate : candidates) {
            try {
                Process p = new ProcessBuilder(candidate, "-c", "import sys; assert sys.version_info >= (3,10)")
                        .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0) { found = candidate; break; }
                p.destroyForcibly();
            } catch (IOException ignored) { }
        }
        if (found == null) throw new IOException("Install Python 3.10+ on the Minecraft server, or set pythonCommand in moviehubtv-common.toml");
        Process check = builder(found, dir, "-c", "import requests,six,certifi").start();
        boolean installed = check.waitFor(15, TimeUnit.SECONDS) && check.exitValue() == 0;
        if (!installed) {
            check.destroyForcibly();
            throw new IOException("Bundled MovieHub Python packages could not load. Reinstall the 0.10.0 jar and use Python 3.10+");
        }
        root = dir; python = found;
    }
    private static ProcessBuilder builder(String executable, Path dir, String... args) {
        List<String> command = new ArrayList<>(); command.add(executable); command.addAll(List.of(args));
        ProcessBuilder b = new ProcessBuilder(command).directory(dir.toFile());
        b.environment().put("PYTHONPATH", dir.resolve("packages") + File.pathSeparator + dir);
        b.environment().put("PYTHONIOENCODING", "utf-8");
        b.redirectError(ProcessBuilder.Redirect.DISCARD);
        if (args.length > 0 && !args[0].equals("-u")) b.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return b;
    }
    public static void search(UUID owner, JsonObject info, Consumer<JsonObject> callback) throws Exception {
        prepare();
        Process process = builder(python, root, "-u", "moviehub_worker.py").start();
        RUNNING.add(process);
        OWNERS.put(owner, process);
        var timeout = TIMER.schedule(process::destroyForcibly, 425, TimeUnit.SECONDS);
        try {
            try (Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(info.toString()); writer.write('\n');
            }
            try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() > 50000) continue;
                    try { callback.accept(JsonParser.parseString(line).getAsJsonObject()); }
                    catch (JsonParseException ignored) { }
                }
            }
            if (process.waitFor() != 0) throw new IOException("MovieHub free-provider engine stopped; verify server Python dependencies");
        } finally {
            timeout.cancel(false); process.destroyForcibly(); RUNNING.remove(process);
            OWNERS.remove(owner, process);
        }
    }
    public static void cancel(UUID owner) { Process p = OWNERS.remove(owner); if (p != null) p.destroyForcibly(); }
    public static void stop() { RUNNING.forEach(Process::destroyForcibly); RUNNING.clear(); OWNERS.clear(); }
}
