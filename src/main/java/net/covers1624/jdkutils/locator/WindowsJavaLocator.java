package net.covers1624.jdkutils.locator;

import net.covers1624.jdkutils.JavaInstall;
import net.covers1624.quack.collection.FastStream;
import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Capable of locating Java installations on a Windows system using both
 * known installation locations, and known Registry Keys.
 * <p>
 * Created by covers1624 on 28/10/21.
 */
public class WindowsJavaLocator extends JavaLocator {

    // Oracle.
    private static final String[] ORACLE = {
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\JavaSoft\\Java Development Kit",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\JavaSoft\\Java Runtime Environment",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\JavaSoft\\JRE",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\JavaSoft\\JDK",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Development Kit",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Runtime Environment",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\JavaSoft\\JRE",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\JavaSoft\\JDK",
    };

    // AdoptOpenJDK, Adoptium predecessor (OpenJDK).
    private static final String[] ADOPT_OPEN_JDK = {
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\AdoptOpenJDK\\JDK",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\AdoptOpenJDK\\JRE",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\AdoptOpenJDK\\JDK",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\AdoptOpenJDK\\JRE"
    };

    // Adoptium, AdoptOpenJDK successor (OpenJDK).
    private static final String[] ADOPTIUM = {
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Eclipse Foundation\\JDK",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Eclipse Foundation\\JRE",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\Eclipse Foundation\\JDK",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\Eclipse Foundation\\JRE",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Eclipse Adoptium\\JDK",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Eclipse Adoptium\\JRE",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\Eclipse Adoptium\\JDK",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\Eclipse Adoptium\\JRE",
    };

    // Microsoft (OpenJDK).
    private static final String[] MICROSOFT = {
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Microsoft\\JDK",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Microsoft\\JRE",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\Microsoft\\JDK",
            /*HKEY_LOCAL_MACHINE\\*/ "SOFTWARE\\Wow6432Node\\Microsoft\\JRE",
    };

    // Common disk locations.
    private static final String[] PATHS = {
            "C:/Program Files/AdoptOpenJDK/",
            "C:/Program Files/Eclipse Foundation/",
            "C:/Program Files/Eclipse Adoptium/",
            "C:/Program Files/Java/",
            "C:/Program Files/Microsoft/",
            "C:/Program Files (x86)/AdoptOpenJDK/",
            "C:/Program Files (x86)/Eclipse Foundation/",
            "C:/Program Files (x86)/Eclipse Adoptium/",
            "C:/Program Files (x86)/Java",
            "C:/Program Files (x86)/Microsoft/",
    };

    WindowsJavaLocator(boolean useJavaw) {
        super(useJavaw);
        Native.init(null);
    }

    @Override
    public List<JavaInstall> findJavaVersions() throws IOException {
        WindowsRegistry registry = Native.get(WindowsRegistry.class);

        List<JavaInstall> installs = new ArrayList<>();
        findAll(registry, installs, ORACLE, "", "JavaHome");
        findAll(registry, installs, ADOPT_OPEN_JDK, join("hotspot", "MSI"), "Path");
        findAll(registry, installs, ADOPTIUM, join("hotspot", "MSI"), "Path");
        findAll(registry, installs, MICROSOFT, join("hotspot", "MSI"), "Path");

        for (String path : PATHS) {
            findJavasInFolder(installs, Paths.get(path));
        }
        return installs;
    }

    private void findAll(WindowsRegistry registry, List<JavaInstall> installs, String[] keys, String keySuffix, String pathKey) throws IOException {
        for (String key : keys) {
            for (String subkey : getSubkeys(registry, WindowsRegistry.Key.HKEY_LOCAL_MACHINE, key)) {
                Path javaHome = getPathValue(registry, WindowsRegistry.Key.HKEY_LOCAL_MACHINE, join(subkey, keySuffix), pathKey);
                if (javaHome == null) continue;

                addJavaInstall(installs, JavaInstall.parse(getJavaExecutable(javaHome)));
            }
        }
    }

    @Nullable
    private static Path getPathValue(WindowsRegistry registry, WindowsRegistry.Key key, String subkey, String value) {
        try {
            return Paths.get(registry.getStringValue(key, subkey, value));
        } catch (MissingRegistryEntryException e) {
            return null;
        }
    }

    private static List<String> getSubkeys(WindowsRegistry registry, WindowsRegistry.Key key, String subkey) {
        try {
            return FastStream.of(registry.getSubkeys(key, subkey))
                    .map(e -> join(subkey, e))
                    .toList();
        } catch (MissingRegistryEntryException e) {
            return Collections.emptyList();
        }
    }

    private static String join(String... strs) {
        return FastStream.of(strs)
                .filter(e -> e != null && !e.isEmpty())
                .join("\\");
    }
}
