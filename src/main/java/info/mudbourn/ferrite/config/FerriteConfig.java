package info.mudbourn.ferrite.config;

import info.mudbourn.ferrite.MmsFerrite;
import info.mudbourn.ferrite.entity.CrammingDispatcher;
import info.mudbourn.ferrite.monitor.HopperHintMonitor;
import info.mudbourn.ferrite.monitor.MonitorLog;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

// Persists module toggles in config/mms_ferrite.properties as deltas from defaults.
public final class FerriteConfig {

    public static final String KEY_CRAMMING = "cramming";
    public static final String KEY_HOPPER_HINT = "hopper-hint";
    public static final String KEY_LOG_MONITORS = "log-monitors";

    private static final String FILE_NAME = "mms_ferrite.properties";
    private static final Properties STATE = new Properties();

    private FerriteConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            STATE.load(in);
        } catch (IOException | IllegalArgumentException e) {
            MmsFerrite.LOGGER.warn("Could not read {}, using defaults: {}", path, e.getMessage());
            STATE.clear();
            return;
        }
        apply();
        MmsFerrite.LOGGER.info("Applied {} saved toggle(s) from {}", STATE.size(), path);
    }

    private static void apply() {
        String value = STATE.getProperty(KEY_CRAMMING);
        if (value != null) {
            CrammingDispatcher.ENABLED = Boolean.parseBoolean(value);
        }
        value = STATE.getProperty(KEY_HOPPER_HINT);
        if (value != null) {
            HopperHintMonitor.USE_HINT = Boolean.parseBoolean(value);
        }
        value = STATE.getProperty(KEY_LOG_MONITORS);
        if (value != null) {
            MonitorLog.ENABLED = Boolean.parseBoolean(value);
        }
        for (String key : STATE.stringPropertyNames()) {
            switch (key) {
                case KEY_CRAMMING, KEY_HOPPER_HINT, KEY_LOG_MONITORS -> {
                }
                default -> MmsFerrite.LOGGER.warn("Unknown key \"{}\" in {} (ignored)", key, FILE_NAME);
            }
        }
    }

    // Passing the compiled-in default removes the key so the file stays delta-only.
    public static synchronized void set(String key, boolean value, boolean defaultValue) {
        if (value == defaultValue) {
            STATE.remove(key);
        } else {
            STATE.setProperty(key, Boolean.toString(value));
        }
        save();
    }

    private static void save() {
        Path path = file();
        try {
            if (STATE.isEmpty()) {
                Files.deleteIfExists(path);
                return;
            }
            Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
            try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                STATE.store(out, "mms-ferrite toggles, deltas from defaults only");
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            MmsFerrite.LOGGER.warn("Could not save {}: {}", path, e.getMessage());
        }
    }
}
