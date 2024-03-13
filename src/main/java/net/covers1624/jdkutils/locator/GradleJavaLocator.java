package net.covers1624.jdkutils.locator;

import net.covers1624.jdkutils.JavaInstall;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by covers1624 on 13/3/24.
 */
public class GradleJavaLocator extends JavaLocator {

    GradleJavaLocator(boolean useJavaw) {
        super(useJavaw);
    }

    @Override
    public List<JavaInstall> findJavaVersions() throws IOException {
        List<JavaInstall> installs = new ArrayList<>();
        for (Path candidatePath : listDir(Paths.get(System.getProperty("user.home"), ".gradle/jdks"))) {
            if (!Files.isDirectory(candidatePath)) continue;

            Path executable = getJavaExecutable(candidatePath);
            if (Files.exists(executable)) {
                addJavaInstall(installs, JavaInstall.parse(executable));
                continue;
            }
            List<Path> inners = listDir(candidatePath);
            if (inners.size() == 1) {
                addJavaInstall(installs, JavaInstall.parse(getJavaExecutable(inners.get(0))));
            }
        }
        return installs;
    }
}
