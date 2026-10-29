package info.mudbourn.ferrite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class RustBridge {

    public static final boolean NATIVE_AVAILABLE = load();

    private RustBridge() {
    }

    // Packed cramming inputs to accumulated pushes, one call per tick.
    public static native void computeCramming(ByteBuffer requests, ByteBuffer results, int entityCount);

    private static boolean load() {
        try {
            String resource = resourcePath();
            Path file = extract(resource);
            System.load(file.toAbsolutePath().toString());
            MmsFerrite.LOGGER.info("Loaded native {}", resource);
            return true;
        } catch (IOException | RuntimeException | LinkageError e) {
            MmsFerrite.LOGGER.warn("Native library unavailable, cramming stays vanilla: {}", e.toString());
            return false;
        }
    }

    private static String resourcePath() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String cpu;
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            cpu = "aarch64";
        } else if (arch.equals("amd64") || arch.equals("x86_64")) {
            cpu = "x86_64";
        } else {
            throw new UnsupportedOperationException("no native library for " + os + " " + arch);
        }
        if (os.contains("mac")) {
            return "/natives/macos-" + cpu + "/libmms_ferrite_native.dylib";
        }
        if (os.contains("linux")) {
            return "/natives/linux-" + cpu + "/libmms_ferrite_native.so";
        }
        if (os.contains("windows") && cpu.equals("x86_64")) {
            return "/natives/windows-x86_64/mms_ferrite_native.dll";
        }
        throw new UnsupportedOperationException("no native library for " + os + " " + arch);
    }

    private static Path extract(String resource) throws IOException {
        try (InputStream in = RustBridge.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing " + resource + " in jar");
            }
            String name = resource.substring(resource.lastIndexOf('/') + 1);
            Path dir = Files.createTempDirectory("mms_ferrite_native");
            Path file = dir.resolve(name);
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            file.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            return file;
        }
    }
}
