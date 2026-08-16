/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.profile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Bounded exact-byte detector for the two operator-installed mod JARs. */
public final class ExactModArtifactDetector {

    private static final int MAX_ROOTS = 4_096;
    private static final int MAX_DESCRIPTOR_BYTES = 1024 * 1024;
    private static final String MOD_DESCRIPTOR = "META-INF/neoforge.mods.toml";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private ExactModArtifactDetector() {
    }

    public static boolean matchesRequiredPair(Iterable<Path> roots) {
        return matches(roots, ExactProfile.ARTIFACTS);
    }

    static boolean matches(Iterable<Path> roots, Map<String, Identity> expected) {
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(expected, "expected");
        if (expected.isEmpty() || expected.entrySet().stream().anyMatch(entry ->
                !entry.getKey().matches("[a-z0-9_.-]+")
                        || entry.getValue().size() <= 0
                        || !SHA256.matcher(entry.getValue().sha256()).matches())) {
            throw new IllegalArgumentException("invalid exact artifact identities");
        }
        Map<String, Path> candidates = new HashMap<>();
        Set<Path> inspected = new HashSet<>();
        int rootCount = 0;
        for (Path root : roots) {
            if (Thread.currentThread().isInterrupted() || ++rootCount > MAX_ROOTS) {
                return false;
            }
            if (root == null || !Files.isRegularFile(root)) {
                continue;
            }
            Path filename = root.getFileName();
            if (filename == null
                    || !filename.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                continue;
            }
            try {
                Path real = root.toRealPath();
                if (!inspected.add(real)) {
                    continue;
                }
                for (String modId : declaredExpectedMods(real, expected.keySet())) {
                    if (candidates.putIfAbsent(modId, real) != null) {
                        return false;
                    }
                }
            } catch (IOException exception) {
                return false;
            }
        }
        if (!candidates.keySet().equals(expected.keySet())) {
            return false;
        }
        try {
            for (Map.Entry<String, Identity> entry : expected.entrySet()) {
                Path candidate = candidates.get(entry.getKey());
                Identity identity = entry.getValue();
                if (Files.size(candidate) != identity.size()
                        || !identity.sha256().equals(digest(candidate))) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static Set<String> declaredExpectedMods(Path jar, Set<String> expected)
            throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry descriptor = zip.getEntry(MOD_DESCRIPTOR);
            if (descriptor == null || descriptor.isDirectory()
                    || descriptor.getSize() > MAX_DESCRIPTOR_BYTES) {
                return Set.of();
            }
            byte[] content;
            try (InputStream input = zip.getInputStream(descriptor)) {
                content = input.readNBytes(MAX_DESCRIPTOR_BYTES + 1);
            }
            if (content.length > MAX_DESCRIPTOR_BYTES) {
                return Set.of();
            }
            Set<String> declared = new HashSet<>();
            boolean inModsTable = false;
            Pattern declaration = Pattern.compile(
                    "^(?:modId|\\\"modId\\\"|'modId')\\s*=\\s*"
                            + "(?:\\\"([a-z0-9_.-]+)\\\"|'([a-z0-9_.-]+)')$"
            );
            for (String line : new String(content, StandardCharsets.UTF_8).split("\\R", -1)) {
                int comment = line.indexOf('#');
                String statement = (comment < 0 ? line : line.substring(0, comment)).trim();
                if (statement.startsWith("[")) {
                    inModsTable = statement.equals("[[mods]]")
                            || statement.equals("[[\"mods\"]]")
                            || statement.equals("[['mods']]");
                } else if (inModsTable) {
                    java.util.regex.Matcher matcher = declaration.matcher(statement);
                    if (matcher.matches()) {
                        String modId = matcher.group(1) == null
                                ? matcher.group(2) : matcher.group(1);
                        if (expected.contains(modId)) {
                            declared.add(modId);
                        }
                    }
                }
            }
            return declared;
        }
    }

    private static String digest(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record Identity(String sha256, long size) {
    }
}
