package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Minimal project-local bootstrapper: downloads the Gradle distribution named in wrapper properties. */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        Path projectRoot = findProjectRoot(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
        Path propertiesFile = projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(propertiesFile)) {
            properties.load(in);
        }
        String distributionUrl = properties.getProperty("distributionUrl", "").replace("\\:", ":");
        if (distributionUrl.isEmpty()) throw new IOException("distributionUrl is missing in " + propertiesFile);
        String archiveName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        String distributionName = archiveName.endsWith(".zip") ? archiveName.substring(0, archiveName.length() - 4) : archiveName;
        String gradleHomeName = distributionName.replaceAll("-(bin|all)$", "");

        String gradleUserHome = System.getenv("GRADLE_USER_HOME");
        if (gradleUserHome == null || gradleUserHome.trim().isEmpty()) {
            gradleUserHome = System.getProperty("user.home") + File.separator + ".gradle";
        }
        Path installDir = Paths.get(gradleUserHome, "wrapper", "dists", distributionName, "android-ai-pet");
        Path gradleHome = installDir.resolve(gradleHomeName);
        Path gradleExecutable = gradleHome.resolve("bin").resolve(isWindows() ? "gradle.bat" : "gradle");
        if (!Files.isRegularFile(gradleExecutable)) {
            Files.createDirectories(installDir);
            Path archive = installDir.resolve(archiveName);
            Path partial = installDir.resolve(archiveName + ".part");
            System.out.println("Downloading Gradle distribution from " + distributionUrl);
            try (InputStream in = new BufferedInputStream(new URL(distributionUrl).openStream())) {
                Files.copy(in, partial, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(partial, archive, StandardCopyOption.REPLACE_EXISTING);
            unzipSafely(archive, installDir);
            Files.deleteIfExists(archive);
        }
        if (!Files.isRegularFile(gradleExecutable)) {
            throw new IOException("Could not find Gradle executable: " + gradleExecutable);
        }

        List<String> command = new ArrayList<>();
        if (isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(gradleExecutable.toString());
        for (String arg : args) command.add(arg);
        Process process = new ProcessBuilder(command).directory(new File(System.getProperty("user.dir"))).inheritIO().start();
        System.exit(process.waitFor());
    }

    private static Path findProjectRoot(Path start) throws IOException {
        Path current = start;
        while (current != null) {
            if (Files.isRegularFile(current.resolve("gradle/wrapper/gradle-wrapper.properties"))) return current;
            current = current.getParent();
        }
        throw new IOException("Could not locate gradle/wrapper/gradle-wrapper.properties from " + start);
    }

    private static void unzipSafely(Path zipFile, Path destination) throws IOException {
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                Path output = normalizedDestination.resolve(entry.getName()).normalize();
                if (!output.startsWith(normalizedDestination)) throw new IOException("Unsafe path in Gradle archive");
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Path parent = output.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(output))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) out.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
