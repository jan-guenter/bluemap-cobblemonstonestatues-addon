// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Jan Guenter
/*
 * Clean-room provenance: SPEC.md; Microsoft Bedrock geometry 1.12 schema/visual docs;
 * sanitized fixture SHA-256 b287b9ff5b6bdd0aa9d7bf04865a1d9bb28f772e59b10333c224ea552fbc848f.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom;

import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.CanonicalQuad;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Cube;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Face;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Quad;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Vec2;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Vec3;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Vertex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.joml.Matrix4f;

/** Raw-f32 multiset parity against the sanitized public-runtime fixture stream. */
public final class RuntimeFixtureParityTest {
    private static final String FIXTURE_SHA256 =
            "b287b9ff5b6bdd0aa9d7bf04865a1d9bb28f772e59b10333c224ea552fbc848f";
    private static final Path DEFAULT_FIXTURE =
            Path.of("/tmp/stone-runtime-fixture/fixtures.ndjson");

    private static final List<Face> CARDINAL_STREAM_ORDER = List.of(
            Face.EAST,
            Face.WEST,
            Face.UP,
            Face.DOWN,
            Face.SOUTH,
            Face.NORTH);

    private static final Map<String, String> EXPECTED_FILTERED_DIGESTS = expectedDigests();

    private RuntimeFixtureParityTest() {
    }

    public static void main(String[] args) throws IOException {
        Path fixture = args.length == 0 ? DEFAULT_FIXTURE : Path.of(args[0]);
        byte[] fixtureBytes = Files.readAllBytes(fixture);
        assertEquals(FIXTURE_SHA256, sha256Hex(fixtureBytes), "fixture provenance hash");

        List<String> lines = Files.readAllLines(fixture, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(13, lines.size(), "fixture case count");

        for (String line : lines) {
            runCase(line);
        }
        System.out.println("RuntimeFixtureParityTest: all 13 cases passed");
    }

    private static void runCase(String line) {
        String id = stringField(line, "id");
        List<String> normalWords = hexArray(line, "cardinal_normal_bits");

        if (id.equals("collapsed_x")) {
            assertTrue(normalWords.stream().allMatch(RuntimeFixtureParityTest::isNaNWord),
                    "collapsed_x must carry only NaN normal words");
            expectIllegalArgument(() -> vector(normalWords, 0),
                    "collapsed_x must fail closed before degeneracy filtering");
            return;
        }

        EnumMap<Face, Vec3> normals = decodeNormals(normalWords);
        List<String> originWords = hexArray(line, "origin_bits");
        List<String> sizeWords = hexArray(line, "size_bits");
        List<String> deformationWords = hexArray(line, "inflate_bits");
        List<Integer> texture = intArray(line, "texture");
        List<Integer> uv = intArray(line, "uv");
        Cube cube = new Cube(
                vector(originWords, 0),
                vector(sizeWords, 0),
                vector(deformationWords, 0),
                booleanField(line, "mirror"),
                new Vec2((float) uv.get(0), (float) uv.get(1)),
                texture.get(0),
                texture.get(1));

        Matrix4f matrix = matrix(hexArray(line, "node_matrix_bits"));
        List<Quad> actual = BedrockBoxReplay.compileRuntimeProfile(
                cube,
                JomlAdapters.finalPositionMatrix(matrix),
                normals);

        List<Quad> capturedCandidates = decodeCapturedCandidates(line, normalWords);
        List<Quad> expected = BedrockBoxReplay.validateAndFilterCandidates(capturedCandidates);
        List<CanonicalQuad> actualCanonical = BedrockBoxReplay.canonicalizeMultiset(actual);
        List<CanonicalQuad> expectedCanonical = BedrockBoxReplay.canonicalizeMultiset(expected);
        assertEquals(expectedCanonical, actualCanonical, id + " raw-f32 quad multiset");

        String actualDigest = HexFormat.of().formatHex(BedrockBoxReplay.canonicalSha256(actual));
        assertEquals(EXPECTED_FILTERED_DIGESTS.get(id), actualDigest, id + " canonical digest");
        if (id.equals("zero_dimension")) {
            assertEquals(2, actual.size(), "zero_dimension retained face multiplicity");
        } else {
            assertEquals(6, actual.size(), id + " retained face count");
        }
    }

    private static List<Quad> decodeCapturedCandidates(
            String line, List<String> cardinalNormalWords) {
        String vertexSuffix = line.substring(line.indexOf("\"vertices\":["));
        Matcher words = Pattern.compile("\"([0-9a-f]{8})\"").matcher(vertexSuffix);
        List<String> allWords = new ArrayList<>(24 * 9);
        while (words.find()) {
            allWords.add(words.group(1));
        }
        assertEquals(24 * 9, allWords.size(), "captured vertex word count");

        List<Quad> result = new ArrayList<>(6);
        for (int quadIndex = 0; quadIndex < 6; quadIndex++) {
            List<Vertex> vertices = new ArrayList<>(4);
            int firstWord = quadIndex * 4 * 9;
            List<String> normal = allWords.subList(firstWord + 5, firstWord + 8);
            Face face = faceForNormal(normal, cardinalNormalWords);
            for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                int offset = (quadIndex * 4 + vertexIndex) * 9;
                vertices.add(new Vertex(
                        vector(allWords, offset),
                        new Vec2(value(allWords.get(offset + 3)), value(allWords.get(offset + 4)))));
                assertEquals(normal, allWords.subList(offset + 5, offset + 8),
                        "constant normal within captured quad");
            }
            result.add(new Quad(face, vertices, vector(normal, 0)));
        }
        return List.copyOf(result);
    }

    private static Face faceForNormal(List<String> normal, List<String> cardinalWords) {
        for (int index = 0; index < CARDINAL_STREAM_ORDER.size(); index++) {
            if (normal.equals(cardinalWords.subList(index * 3, index * 3 + 3))) {
                return CARDINAL_STREAM_ORDER.get(index);
            }
        }
        throw new AssertionError("captured normal is absent from cardinal stream: " + normal);
    }

    private static EnumMap<Face, Vec3> decodeNormals(List<String> words) {
        assertEquals(18, words.size(), "cardinal normal word count");
        EnumMap<Face, Vec3> result = new EnumMap<>(Face.class);
        for (int index = 0; index < CARDINAL_STREAM_ORDER.size(); index++) {
            result.put(CARDINAL_STREAM_ORDER.get(index), vector(words, index * 3));
        }
        return result;
    }

    private static Matrix4f matrix(List<String> words) {
        assertEquals(16, words.size(), "matrix word count");
        float[] m = words.stream().map(RuntimeFixtureParityTest::value)
                .collect(
                        () -> new FloatArrayCollector(16),
                        FloatArrayCollector::add,
                        FloatArrayCollector::merge)
                .array();
        return new Matrix4f(
                m[0], m[1], m[2], m[3],
                m[4], m[5], m[6], m[7],
                m[8], m[9], m[10], m[11],
                m[12], m[13], m[14], m[15]);
    }

    private static Vec3 vector(List<String> words, int offset) {
        return new Vec3(
                value(words.get(offset)),
                value(words.get(offset + 1)),
                value(words.get(offset + 2)));
    }

    private static float value(String word) {
        return Float.intBitsToFloat((int) Long.parseLong(word, 16));
    }

    private static boolean isNaNWord(String word) {
        return Float.isNaN(value(word));
    }

    private static String stringField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\":\"([^\"]*)\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("missing string field " + name);
        }
        return matcher.group(1);
    }

    private static boolean booleanField(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\":(true|false)")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("missing boolean field " + name);
        }
        return Boolean.parseBoolean(matcher.group(1));
    }

    private static List<String> hexArray(String json, String name) {
        String body = arrayBody(json, name);
        Matcher matcher = Pattern.compile("\"([0-9a-f]{8})\"").matcher(body);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return List.copyOf(result);
    }

    private static List<Integer> intArray(String json, String name) {
        String body = arrayBody(json, name);
        Matcher matcher = Pattern.compile("-?[0-9]+").matcher(body);
        List<Integer> result = new ArrayList<>();
        while (matcher.find()) {
            result.add(Integer.parseInt(matcher.group()));
        }
        return List.copyOf(result);
    }

    private static String arrayBody(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\":\\[([^\\]]*)\\]")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("missing simple array field " + name);
        }
        return matcher.group(1);
    }

    private static Map<String, String> expectedDigests() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("asymmetric_uv", "601499526bf7784bd10e3e7eb09150ab79432d272c7051cb2538359c63887ac6");
        result.put("mirror", "63977c12246e027f0bbb386c44e1db91b1250c6fd3c8cda91a9d1f34060fdff6");
        result.put("signed_size", "a6755f7d4ea7cc1b1ab4b1fcf1b81088f85762f041b21058db773ed283c0018c");
        result.put("negative_inflate", "d184c8922626973a23efdd2a0438cc87cfa171f483f3be321168a7a81824e93f");
        result.put("negative_fractional_uv", "d43d8964f99583af42947931e0a6fd3731197a0b75c515df6875fbbaf716c874");
        result.put("fractional_size_negative_uv", "320fc334141894e63047dd0f2013cbc703c55c541b23584facb1d2cd764a307b");
        result.put("inflate_zero_control", "3ba03c5527c4a30d9dfd27e2909193ab620e3640ad0aceeb12236f48a61a5059");
        result.put("positive_uniform_inflate", "d240bb505d386796a2ee0c7db175babf93ecddf08f7b2187b745e2301c7d41c2");
        result.put(
                "negative_size_x_positive_inflate",
                "5219570f950e4c6cdad58865a93ac515ae5a9c492ca7664fae612a2145988a78"
        );
        result.put(
                "negative_size_x_negative_inflate",
                "6655f004589e430194256f02746c184ce37aa25f1bbf4804221b0fad7e7985f0"
        );
        result.put("zero_dimension", "142ee742707ffebb3f78c6168cff784b84e8a3eab8eb20c89c72aaf96cb0b431");
        result.put("negative_handedness", "a7396c32abbe2ba59f4435f1d53f2c4e3af255dab1416d99744f97975ae9fc01");
        return Map.copyOf(result);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 unavailable", impossible);
        }
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!(expected == null ? actual == null : expected.equals(actual))) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static final class FloatArrayCollector {
        private final float[] values;
        private int size;

        private FloatArrayCollector(int capacity) {
            values = new float[capacity];
        }

        private void add(float value) {
            values[size++] = value;
        }

        private void merge(FloatArrayCollector other) {
            for (int index = 0; index < other.size; index++) {
                add(other.values[index]);
            }
        }

        private float[] array() {
            if (size != values.length) {
                throw new AssertionError("expected " + values.length + " floats, got " + size);
            }
            return values.clone();
        }
    }
}
