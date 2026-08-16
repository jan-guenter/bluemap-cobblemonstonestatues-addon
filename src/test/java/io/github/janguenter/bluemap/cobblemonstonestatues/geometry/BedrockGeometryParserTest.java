/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockGeometryParserTest {

    private static final String EXACT_COBBLEMON_SHA256 =
            "962d75df4fb649d94863a7a7d130d4d2b3de4da9b3cae4c44b1ce90f37ec0ed5";
    private static final String JOLTIK_PATH =
            "assets/cobblemon/bedrock/pokemon/models/0595_joltik/joltik.geo.json";

    @Test
    void buildsExactCobblemonHierarchySemanticsFromSupportedBoxSchema() {
        BedrockGeometry geometry = BedrockGeometryParser.parse(synthetic().getBytes(
                java.nio.charset.StandardCharsets.UTF_8
        ));
        BakedGeometryTopology topology = BakedGeometryTopology.bake(geometry);

        assertEquals("geometry.synthetic", topology.identifier());
        assertEquals("1.12.0", topology.sourceFormatVersion());
        assertEquals(2, topology.sourceBoneCount());
        assertEquals(3, topology.sourceCubeCount());
        assertEquals(2, topology.sourceLocatorCount());
        assertEquals(7, topology.nodes().size());
        assertEquals("/", topology.nodes().get(0).path());
        assertEquals("/body", topology.nodes().get(1).path());
        assertEquals("/body/%25body%250", topology.nodes().get(2).path());
        assertEquals("/body/child", topology.nodes().get(3).path());
        assertEquals("/body/internal_locator__array", topology.nodes().get(4).path());
        assertEquals("/body/internal_locator__object", topology.nodes().get(5).path());
        assertEquals("/internal_locator__root", topology.nodes().get(6).path());

        BakedGeometryTopology.Node rootBone = topology.nodes().get(1);
        assertEquals(zero(), rootBone.defaultTransform().position());
        assertEquals(zero(), rootBone.defaultTransform().rotationRadians());
        assertEquals(1, rootBone.boxes().size());
        BakedGeometryTopology.Box flat = rootBone.boxes().getFirst();
        assertEquals(new BedrockGeometry.Vec3(-2F, -0.0F, -2F), flat.origin());
        assertEquals(new BedrockGeometry.Vec3(2F, 0F, 4F), flat.size());
        assertEquals(0.25F, flat.inflate());
        assertTrue(flat.mirror());

        BakedGeometryTopology.Node rotated = topology.nodes().get(2);
        assertEquals(new BedrockGeometry.Vec3(4F, -5F, 6F),
                rotated.defaultTransform().position());
        assertEquals((float) (Math.PI / 2D),
                rotated.defaultTransform().rotationRadians().y());
        assertEquals(new BedrockGeometry.Vec3(-4F, 2F, -6F),
                rotated.boxes().getFirst().origin());

        BakedGeometryTopology.Node child = topology.nodes().get(3);
        assertEquals(new BedrockGeometry.Vec3(3F, -4F, 5F),
                child.defaultTransform().position());
        assertEquals((float) Math.toRadians(30D),
                child.defaultTransform().rotationRadians().z());
        assertEquals(
                Float.floatToRawIntBits((float) Math.toRadians(30D)),
                Float.floatToRawIntBits(child.defaultTransform().rotationRadians().z())
        );
        assertFalse(child.boxes().getFirst().mirror());

        assertEquals(new BedrockGeometry.Vec3(-0.0F, 0F, -0.0F),
                topology.nodes().get(4).defaultTransform().position());
        assertEquals(
                Float.floatToRawIntBits(-0.0F),
                Float.floatToRawIntBits(topology.nodes().get(4)
                        .defaultTransform().position().x())
        );
        assertEquals(
                Float.floatToRawIntBits(-0.0F),
                Float.floatToRawIntBits(topology.nodes().get(4)
                        .defaultTransform().position().z())
        );
        assertEquals((float) Math.toRadians(45D),
                topology.nodes().get(5).defaultTransform().rotationRadians().x());
    }

    @Test
    void rejectsUnsupportedOrAmbiguousGeometryInsteadOfGuessing() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.bad","texture_width":16,
                    "texture_height":16},
                  "bones":[{"name":"body","pivot":[0,0,0],"custom":true}]
                }]}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.bad","texture_width":16,
                    "texture_height":16},
                  "bones":[{"name":"child","parent":"later","pivot":[0,0,0]},
                    {"name":"later","pivot":[0,0,0]}]
                }]}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.bad","texture_width":16,
                    "texture_height":16},
                  "bones":[{"name":"body","pivot":[0,0,0],"cubes":[{
                    "origin":[0,0,0],"size":[1,1,1],"pivot":[0,0,0],
                    "rotation":[0,1,0],"uv":{"north":{"uv":[0,0]}}
                  }]}]
                }]}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.bad","texture_width":16,
                    "texture_height":16},
                  "bones":[{"name":"body","pivot":[0,0,0],"cubes":[{
                    "origin":[0,0,0],"size":[1,1,1],"rotation":[0,1,0],"uv":[0,0]
                  }]}]
                }]}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.bad","texture_width":16,
                    "texture_height":16},"bones":[{"name":"body","pivot":[0,0,0]}]
                },{
                  "description":{"identifier":"geometry.other","texture_width":16,
                    "texture_height":16},"bones":[{"name":"body","pivot":[0,0,0]}]
                }]}
                """));
    }

    @Test
    void acceptsKnownOneTwentyOneBoxSchemaAndIgnoresValidatedBoneMirrorLikeCobblemon() {
        BedrockGeometry geometry = BedrockGeometryParser.parse("""
                {"format_version":"1.21.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.new","texture_width":16,
                    "texture_height":16},
                  "bones":[{"name":"body","pivot":[0,0,0],"mirror":true,
                    "cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]}]
                }]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        BakedGeometryTopology topology = BakedGeometryTopology.bake(geometry);
        assertEquals("1.21.0", topology.sourceFormatVersion());
        assertFalse(topology.nodes().get(1).boxes().getFirst().mirror());
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"format_version":"1.21.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.bad","texture_width":16,
                    "texture_height":16},
                  "bones":[{"name":"body","pivot":[0,0,0],"mirror":"true"}]
                }]}
                """));
    }

    @Test
    void bindsOrderNeutralTopologyToCapturedClientPreOrderAndRejectsRosterDrift() {
        BakedGeometryTopology topology = BakedGeometryTopology.bake(
                BedrockGeometryParser.parse(synthetic().getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                ))
        );
        List<String> capturedOrder = List.of(
                "/body",
                "/body/internal_locator__object", "/body/internal_locator__array",
                "/body/child", "/body/%25body%250"
        );
        PoseState state = poseState(capturedOrder, "/body", topology);

        BakedGeometryTopology.BoundGeometry bound = topology.bind(state);
        assertEquals(capturedOrder, bound.nodes().stream()
                .map(node -> node.geometry().path()).toList());
        assertFalse(bound.nodes().stream().anyMatch(node ->
                node.geometry().path().equals("/internal_locator__root")));

        List<PoseState.Node> badNodes = new ArrayList<>(state.nodes());
        PoseState.Node original = badNodes.getLast();
        badNodes.set(badNodes.size() - 1, new PoseState.Node(
                "/body/unknown", original.parentIndex(), original.siblingOrdinal(),
                original.runtimeFrame(),
                original.visible(), original.skipDraw()
        ));
        PoseState drifted = new PoseState("/body", state.tintArgb(), badNodes);
        assertThrows(IllegalArgumentException.class, () -> topology.bind(drifted));

        List<String> wrapperOrder = topology.nodes().stream()
                .map(BakedGeometryTopology.Node::path).toList();
        PoseState wrapperState = poseState(wrapperOrder, "/", topology);
        assertEquals(wrapperOrder, topology.bind(wrapperState).nodes().stream()
                .map(node -> node.geometry().path()).toList());

        List<PoseState.Node> missingFrame = new ArrayList<>(state.nodes());
        int drawNode = java.util.stream.IntStream.range(0, missingFrame.size())
                .filter(index -> missingFrame.get(index).runtimeFrame() != null)
                .findFirst().orElseThrow();
        PoseState.Node draw = missingFrame.get(drawNode);
        missingFrame.set(drawNode, new PoseState.Node(
                draw.path(), draw.parentIndex(), draw.siblingOrdinal(), null,
                draw.visible(), draw.skipDraw()
        ));
        assertThrows(IllegalArgumentException.class, () -> topology.bind(
                new PoseState("/body", state.tintArgb(), missingFrame)
        ));

        List<PoseState.Node> suppressed = new ArrayList<>(state.nodes());
        suppressed.set(drawNode, new PoseState.Node(
                draw.path(), draw.parentIndex(), draw.siblingOrdinal(), null,
                draw.visible(), true
        ));
        BakedGeometryTopology.BoundGeometry suppressedBound = topology.bind(
                new PoseState("/body", state.tintArgb(), suppressed)
        );
        assertTrue(suppressedBound.nodes().get(drawNode).pose().skipDraw());
        assertThrows(IllegalArgumentException.class, () -> new PoseState(
                "/body", state.tintArgb(), replace(suppressed, drawNode,
                new PoseState.Node(
                        draw.path(), draw.parentIndex(), draw.siblingOrdinal(),
                        draw.runtimeFrame(), draw.visible(), true
                ))
        ));
    }

    @Test
    void parsesExactPinnedJoltikWhenArtifactPathIsSupplied() throws IOException {
        String configured = System.getProperty("cobblemonJar");
        Assumptions.assumeTrue(configured != null, "exact Cobblemon JAR was not supplied");
        Path jar = Path.of(configured);
        assertTrue(Files.isRegularFile(jar));
        assertEquals(EXACT_COBBLEMON_SHA256, sha256(jar));

        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(JOLTIK_PATH);
            assertEquals(5_988L, entry.getSize());
            byte[] raw;
            try (InputStream input = zip.getInputStream(entry)) {
                raw = input.readAllBytes();
            }
            assertEquals(
                    "864070a78b13e9f8ce608210e3772d6d494d0c9e4621a8c631680368993c9d7f",
                    sha256(raw)
            );
            BedrockGeometry geometry = BedrockGeometryParser.parse(raw);
            BakedGeometryTopology topology = BakedGeometryTopology.bake(geometry);
            assertEquals("geometry.joltik", geometry.identifier());
            assertEquals(45, geometry.bones().size());
            assertEquals(37, geometry.cubeCount());
            assertEquals(0, geometry.locatorCount());
            assertEquals(47, topology.nodes().size());
            assertEquals(37, topology.nodes().stream().mapToInt(node -> node.boxes().size()).sum());

        }
    }

    private static void parse(String json) {
        BedrockGeometryParser.parse(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static BedrockGeometry.Vec3 zero() {
        return new BedrockGeometry.Vec3(0F, 0F, 0F);
    }

    private static PoseState poseState(
            List<String> paths,
            String selectedRoot,
            BakedGeometryTopology topology
    ) {
        java.util.Map<String, BakedGeometryTopology.Node> topologyByPath =
                topology.nodes().stream().collect(java.util.stream.Collectors.toMap(
                        BakedGeometryTopology.Node::path, java.util.function.Function.identity()
                ));
        List<PoseState.Node> nodes = new ArrayList<>();
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            int parentIndex = index == 0 ? -1 : paths.indexOf(parent(path));
            if (index > 0 && parentIndex < 0) {
                throw new IllegalArgumentException("test path order lacks parent");
            }
            int ordinal = 0;
            for (int earlier = 1; earlier < index; earlier++) {
                if (parent(paths.get(earlier)).equals(parent(path))) {
                    ordinal++;
                }
            }
            PoseState.RuntimeFrame frame = topologyByPath.get(path).boxes().isEmpty()
                    ? null : new PoseState.RuntimeFrame(
                            identityMatrix(), identityNormals(), mirrorIdentityNormals()
                    );
            nodes.add(new PoseState.Node(
                    path, parentIndex, ordinal, frame,
                    true, false
            ));
        }
        return new PoseState(selectedRoot, 0xFFFFFFFF, nodes);
    }

    private static List<PoseState.Node> replace(
            List<PoseState.Node> source, int index, PoseState.Node replacement
    ) {
        List<PoseState.Node> result = new ArrayList<>(source);
        result.set(index, replacement);
        return result;
    }

    private static PoseState.PositionMatrix identityMatrix() {
        return new PoseState.PositionMatrix(
                1F, 0F, 0F, 0F,
                0F, 1F, 0F, 0F,
                0F, 0F, 1F, 0F,
                0F, 0F, 0F, 1F
        );
    }

    private static PoseState.CardinalNormals identityNormals() {
        return new PoseState.CardinalNormals(
                0F, -1F, 0F, 0F, 1F, 0F,
                -1F, 0F, 0F, 0F, 0F, -1F,
                1F, 0F, 0F, 0F, 0F, 1F
        );
    }

    private static PoseState.MirrorZeroXNormals mirrorIdentityNormals() {
        return new PoseState.MirrorZeroXNormals(
                -0.0F, -1F, 0F, -0.0F, 1F, 0F,
                -0.0F, 0F, -1F, -0.0F, 0F, 1F
        );
    }

    private static String parent(String path) {
        int separator = path.lastIndexOf('/');
        return separator == 0 ? "/" : path.substring(0, separator);
    }

    private static String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = digest();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    private static String sha256(byte[] value) {
        return HexFormat.of().formatHex(digest().digest(value));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String synthetic() {
        return """
                {
                  "format_version":"1.12.0",
                  "minecraft:geometry":[{
                    "description":{
                      "identifier":"geometry.synthetic",
                      "texture_width":64,
                      "texture_height":32,
                      "visible_bounds_width":3,
                      "visible_bounds_height":4,
                      "visible_bounds_offset":[0,1,0]
                    },
                    "bones":[{
                      "name":"body",
                      "pivot":[1,2,3],
                      "rotation":[10,20,30],
                      "cubes":[{
                        "origin":[-1,2,1],
                        "size":[2,0,4],
                        "uv":[4,8],
                        "inflate":0.25,
                        "mirror":true
                      },{
                        "origin":[1,2,3],
                        "size":[2,3,4],
                        "pivot":[5,7,9],
                        "rotation":[0,90,0],
                        "uv":[10,12]
                      }],
                      "locators":{
                        "array":[1,2,3],
                        "object":{"offset":[3,4,5],"rotation":[45,0,0]}
                      }
                    },{
                      "name":"child",
                      "parent":"body",
                      "pivot":[4,6,8],
                      "rotation":[0,0,30],
                      "cubes":[{
                        "origin":[4,5,8],"size":[1,1,1],"uv":[0,0]
                      }]
                    }]
                  }]
                }
                """;
    }
}
