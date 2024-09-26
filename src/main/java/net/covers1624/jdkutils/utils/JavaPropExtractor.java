package net.covers1624.jdkutils.utils;

import net.covers1624.quack.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by covers1624 on 27/9/24.
 */
public class JavaPropExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaPropExtractor.class);
    private static final boolean DEBUG = Boolean.getBoolean("net.covers1624.jdkutils.utils.JavaPropExtractor.debug");

    /**
     * Attempt to extract the given properties from the given java executable.
     *
     * @param executable     The executable to run.
     * @param propsToExtract The properties to extract.
     * @return The extracted properties.
     */
    public static @Nullable Map<String, String> extractProperties(Path executable, List<String> propsToExtract) {
        if (DEBUG) {
            LOGGER.info("Attempting to parse properties from executable '{}'", executable);
        }
        if (Files.notExists(executable)) {
            if (DEBUG) {
                LOGGER.error(" Executable does not exist!");
            }
            return null;
        }
        try {
            Path tempDir = Files.createTempDirectory("java_prop_extract");
            JavaPropExtractGenerator.writeClass(tempDir);
            List<String> args = new LinkedList<>(Arrays.asList(
                    executable.toRealPath().toString(),
                    "-Dfile.encoding=UTF8",
                    "-cp",
                    ".",
                    "PropExtract"
            ));
            args.addAll(propsToExtract);
            ProcessBuilder builder = new ProcessBuilder()
                    .directory(tempDir.toFile())
                    .command(args);

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Process process = builder.start();
            IOUtils.copy(process.getInputStream(), os);
            try {
                boolean exited = process.waitFor(30, TimeUnit.SECONDS);
                if (!exited) {
                    LOGGER.warn("Waited more than 30 seconds for {}. Force closing..", executable);
                    process.destroyForcibly();
                    return null;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted.", e);
            }

            Map<String, String> properties = new HashMap<>();
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] split = line.split("=", 2);
                    if (split.length != 2) continue;
                    // convert null string back to a real null.
                    if (split[1].equals("null")) {
                        split[1] = null;
                    }
                    properties.put(split[0], split[1]);
                }
            }
            return properties;
        } catch (Throwable e) {
            if (DEBUG) {
                LOGGER.error("Failed to parse Java install.", e);
            }
            return null;
        }
    }
}
