/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap522;

import de.bluecolored.bluemap.core.resources.pack.Pack;
import de.bluecolored.bluemap.core.resources.pack.PackVersion;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import io.github.janguenter.bluemap.cobblemonstonestatues.activation.ResourceBlob;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.ModelIdentity;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.PoseCatalog.TextureIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveResourceLoaderTest {

    private static final String MODEL_PATH =
            "assets/cobblemon/bedrock/pokemon/models/test.geo.json";
    private static final String TEXTURE_PATH =
            "assets/cobblemon/textures/pokemon/test.png";

    @TempDir
    Path temporary;

    @Test
    void capturesTheFirstVisibleGeoAndPngInResourceCallbackOrder() throws Exception {
        byte[] highModel = bytes("high-model");
        byte[] highTexture = bytes("high-texture");
        byte[] lowModel = bytes("low-model");
        byte[] lowTexture = bytes("low-texture");
        Path high = root("high", highModel, highTexture);
        Path low = root("low", lowModel, lowTexture);
        ResourcePack pack = new ResourcePack(new PackVersion(34, 0));

        ActiveResourceLoader.Result highFirst = ActiveResourceLoader.load(
                pack, List.of(high, low), models(highModel), textures(highTexture)
        );
        ActiveResourceLoader.Result lowFirst = ActiveResourceLoader.load(
                pack, List.of(low, high), models(lowModel), textures(lowTexture)
        );

        assertTrue(highFirst.valid());
        assertArrayEquals(highModel, read(
                highFirst.models().get("cobblemon:test.geo")
        ));
        assertArrayEquals(highTexture, highFirst.textures().get(
                "cobblemon:textures/pokemon/test.png"
        ).openStream().readAllBytes());
        assertTrue(lowFirst.valid());
        assertArrayEquals(lowModel, read(
                lowFirst.models().get("cobblemon:test.geo")
        ));
        assertArrayEquals(lowTexture, lowFirst.textures().get(
                "cobblemon:textures/pokemon/test.png"
        ).openStream().readAllBytes());
    }

    @Test
    void wrongHigherPriorityGeoOrPngCannotBeRepairedByALowerRoot() throws Exception {
        byte[] highModel = bytes("wrong-high-model");
        byte[] highTexture = bytes("wrong-high-texture");
        byte[] lowModel = bytes("expected-low-model");
        byte[] lowTexture = bytes("expected-low-texture");
        Path high = root("high", highModel, highTexture);
        Path low = root("low", lowModel, lowTexture);
        ResourcePack pack = new ResourcePack(new PackVersion(34, 0));

        ActiveResourceLoader.Result modelDrift = ActiveResourceLoader.load(
                pack, List.of(high, low), models(lowModel), textures(highTexture)
        );
        ActiveResourceLoader.Result textureDrift = ActiveResourceLoader.load(
                pack, List.of(high, low), models(highModel), textures(lowTexture)
        );

        assertFalse(modelDrift.valid());
        assertEquals("active-model-hash-drift", modelDrift.reason());
        assertFalse(textureDrift.valid());
        assertEquals("active-texture-hash-drift", textureDrift.reason());
    }

    @Test
    void equalSizeWrongDigestIsStillATerminalFirstWinner() throws Exception {
        byte[] wrong = bytes("aaaaaaaa");
        byte[] expected = bytes("bbbbbbbb");
        Path high = root("high", wrong, wrong);
        Path low = root("low", expected, expected);

        ActiveResourceLoader.Result result = ActiveResourceLoader.load(
                new ResourcePack(new PackVersion(34, 0)), List.of(high, low),
                models(expected), textures(expected)
        );

        assertFalse(result.valid());
        assertEquals("active-model-hash-drift", result.reason());
    }

    @Test
    void swallowedFirstReadFailureCannotFallThroughToALowerRoot() throws Exception {
        byte[] expected = bytes("expected");
        Path high = root("high", expected, expected);
        Path low = root("low", expected, expected);
        AtomicInteger lowModelReads = new AtomicInteger();

        ActiveResourceLoader.Result result = ActiveResourceLoader.load(
                swallowingPack(), List.of(high, low),
                models(expected), textures(expected), (path, size) -> {
                    if (path.startsWith(high) && path.endsWith("test.geo.json")) {
                        throw new IOException("injected first-winner read failure");
                    }
                    if (path.startsWith(low) && path.endsWith("test.geo.json")) {
                        lowModelReads.incrementAndGet();
                    }
                    return ResourceBlob.read(path, size);
                }
        );

        assertFalse(result.valid());
        assertEquals("active-model-hash-drift", result.reason());
        assertEquals(0, lowModelReads.get());
    }

    @Test
    void aggregateBudgetRejectsBeforeRootIterationAndAcceptsExactBoundary() throws Exception {
        Map<String, ModelIdentity> exact = manyModels(32);
        Map<String, ModelIdentity> over = manyModels(33);
        Iterable<Path> mustNotIterate = () -> {
            throw new AssertionError("roots were opened before aggregate rejection");
        };

        ActiveResourceLoader.Result rejected = ActiveResourceLoader.load(
                new ResourcePack(new PackVersion(34, 0)), mustNotIterate,
                over, Map.of()
        );
        assertFalse(rejected.valid());
        assertEquals("active-resource-byte-budget", rejected.reason());

        ActiveResourceLoader.Result exactBoundary = ActiveResourceLoader.load(
                new ResourcePack(new PackVersion(34, 0)), List.of(), exact, Map.of()
        );
        assertFalse(exactBoundary.valid());
        assertEquals("active-resource-roster-mismatch", exactBoundary.reason());
        assertEquals(FormatTwoBudgets.MAX_ACTIVE_RESOURCE_BYTES,
                exact.values().stream().mapToLong(ModelIdentity::size).sum());
    }

    private Path root(String name, byte[] model, byte[] texture) throws IOException {
        Path root = temporary.resolve(name);
        Path modelPath = root.resolve(MODEL_PATH);
        Path texturePath = root.resolve(TEXTURE_PATH);
        Files.createDirectories(modelPath.getParent());
        Files.createDirectories(texturePath.getParent());
        Files.write(modelPath, model);
        Files.write(texturePath, texture);
        return root;
    }

    private static ResourcePack swallowingPack() {
        return new ResourcePack(new PackVersion(34, 0)) {
            @Override
            public void loadResourcePath(Path root, Pack.Loader loader) {
                try {
                    loader.load(root);
                } catch (IOException ignored) {
                    // Mirrors BlueMap's archive-root callback containment.
                }
            }
        };
    }

    private static Map<String, ModelIdentity> models(byte[] raw) {
        ModelIdentity identity = new ModelIdentity(
                "cobblemon:test.geo",
                "cobblemon:bedrock/pokemon/models/test.geo.json",
                MODEL_PATH, "mod/cobblemon", raw.length, sha256(raw), "1.12.0",
                "geometry.test", 64, 64, "a".repeat(64), 1, 1, 1, 0
        );
        return Map.of(identity.modelId(), identity);
    }

    private static Map<String, ModelIdentity> manyModels(int count) {
        Map<String, ModelIdentity> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String name = "test" + index;
            ModelIdentity identity = new ModelIdentity(
                    "cobblemon:" + name + ".geo",
                    "cobblemon:bedrock/pokemon/models/" + name + ".geo.json",
                    "assets/cobblemon/bedrock/pokemon/models/" + name + ".geo.json",
                    "mod/cobblemon", FormatTwoBudgets.MAX_RESOURCE_BYTES,
                    String.format("%064x", index + 1), "1.12.0",
                    "geometry." + name, 64, 64, "a".repeat(64), 1, 1, 1, 0
            );
            values.put(identity.modelId(), identity);
        }
        return values;
    }

    private static Map<String, TextureIdentity> textures(byte[] raw) {
        TextureIdentity identity = new TextureIdentity(
                "cobblemon:textures/pokemon/test.png", TEXTURE_PATH,
                "mod/cobblemon", raw.length, sha256(raw), 1, 1
        );
        return Map.of(identity.resourceId(), identity);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] read(ResourceBlob blob) throws IOException {
        return blob.openStream().readAllBytes();
    }

    private static String sha256(byte[] raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
