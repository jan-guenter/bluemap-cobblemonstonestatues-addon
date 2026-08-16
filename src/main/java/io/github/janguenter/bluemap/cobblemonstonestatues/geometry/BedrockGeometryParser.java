/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses only the exact ModelPart-compatible Bedrock box schema used by the hybrid compiler. */
public final class BedrockGeometryParser {

    public static final int MAX_BYTES = FormatTwoBudgets.MAX_RESOURCE_BYTES;
    public static final int MAX_BONES = FormatTwoBudgets.MAX_MODEL_BONES;
    public static final int MAX_CUBES = FormatTwoBudgets.MAX_MODEL_CUBES;
    public static final int MAX_LOCATORS = FormatTwoBudgets.MAX_MODEL_LOCATORS;
    private static final int MAX_TEXTURE_SIZE = 16_384;
    private static final int MAX_STRING_BYTES = 4_096;
    private static final float MAX_ABSOLUTE_VALUE = 1_000_000F;
    private static final Set<String> ROOT_KEYS = Set.of("format_version", "minecraft:geometry");
    private static final Set<String> GEOMETRY_KEYS = Set.of("description", "bones");
    private static final Set<String> DESCRIPTION_KEYS = Set.of(
            "identifier", "texture_width", "texture_height", "visible_bounds_width",
            "visible_bounds_height", "visible_bounds_offset"
    );
    private static final Set<String> BONE_KEYS = Set.of(
            "name", "parent", "pivot", "rotation", "cubes", "locators", "mirror"
    );
    private static final Set<String> CUBE_KEYS = Set.of(
            "origin", "size", "pivot", "rotation", "uv", "inflate", "mirror"
    );
    private static final Set<String> LOCATOR_KEYS = Set.of("offset", "rotation");

    private BedrockGeometryParser() {
    }

    public static BedrockGeometry parse(byte[] raw) {
        if (raw.length < 2 || raw.length > MAX_BYTES) {
            throw new IllegalArgumentException("GEO bytes outside budget");
        }
        JsonObject root;
        try {
            root = JsonParser.parseString(strictUtf8(raw)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("invalid GEO JSON", exception);
        }
        requireKeys(root, ROOT_KEYS, "GEO root");
        String formatVersion = string(root, "format_version", true);
        if (!Set.of("1.12.0", "1.21.0").contains(formatVersion)) {
            throw new IllegalArgumentException("unsupported GEO format version");
        }
        JsonArray geometries = array(root, "minecraft:geometry", true);
        if (geometries.size() != 1 || !geometries.get(0).isJsonObject()) {
            throw new IllegalArgumentException("GEO must contain exactly one geometry");
        }
        return parseGeometry(formatVersion, geometries.get(0).getAsJsonObject());
    }

    private static BedrockGeometry parseGeometry(String formatVersion, JsonObject geometry) {
        requireKeys(geometry, GEOMETRY_KEYS, "geometry");
        JsonObject description = object(geometry, "description", true);
        requireKeys(description, DESCRIPTION_KEYS, "geometry description");
        String identifier = string(description, "identifier", true);
        int textureWidth = boundedInteger(
                description, "texture_width", true, 1, MAX_TEXTURE_SIZE
        );
        int textureHeight = boundedInteger(
                description, "texture_height", true, 1, MAX_TEXTURE_SIZE
        );
        optionalFiniteNumber(description, "visible_bounds_width");
        optionalFiniteNumber(description, "visible_bounds_height");
        optionalVector(description, "visible_bounds_offset");

        JsonArray boneArray = array(geometry, "bones", true);
        if (boneArray.isEmpty() || boneArray.size() > MAX_BONES) {
            throw new IllegalArgumentException("GEO bone count outside budget");
        }
        List<BedrockGeometry.Bone> bones = new ArrayList<>(boneArray.size());
        Set<String> names = new HashSet<>();
        java.util.Map<String, Integer> depths = new java.util.HashMap<>();
        int cubeCount = 0;
        int locatorCount = 0;
        for (JsonElement element : boneArray) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("GEO bone is not an object");
            }
            BedrockGeometry.Bone bone = parseBone(
                    element.getAsJsonObject(),
                    Math.subtractExact(MAX_CUBES, cubeCount),
                    Math.subtractExact(MAX_LOCATORS, locatorCount)
            );
            if (!names.add(bone.name())) {
                throw new IllegalArgumentException("duplicate GEO bone name");
            }
            if (bone.parent() != null && !names.contains(bone.parent())) {
                throw new IllegalArgumentException("GEO parent is absent or appears after child");
            }
            int depth = bone.parent() == null ? 1 : Math.addExact(
                    depths.get(bone.parent()), 1
            );
            if (depth > FormatTwoBudgets.MAX_MODEL_DEPTH) {
                throw new IllegalArgumentException("GEO parent depth outside budget");
            }
            depths.put(bone.name(), depth);
            cubeCount = Math.addExact(cubeCount, bone.cubes().size());
            locatorCount = Math.addExact(locatorCount, bone.locators().size());
            if (cubeCount > MAX_CUBES || locatorCount > MAX_LOCATORS) {
                throw new IllegalArgumentException("GEO child count outside budget");
            }
            bones.add(bone);
        }
        return new BedrockGeometry(
                formatVersion, identifier, textureWidth, textureHeight,
                bones, cubeCount, locatorCount
        );
    }

    private static BedrockGeometry.Bone parseBone(
            JsonObject object, int remainingCubes, int remainingLocators
    ) {
        requireKeys(object, BONE_KEYS, "bone");
        String name = string(object, "name", true);
        String parent = string(object, "parent", false);
        BedrockGeometry.Vec3 pivot = vector(object, "pivot", true);
        BedrockGeometry.Vec3 rotation = vector(object, "rotation", false);
        bool(object, "mirror", false, false);
        JsonArray cubeArray = array(object, "cubes", false);
        JsonElement locatorElement = object.get("locators");
        if (cubeArray != null && cubeArray.size() > remainingCubes) {
            throw new IllegalArgumentException("GEO cube count exceeds remaining budget");
        }
        if (locatorElement != null && !locatorElement.isJsonNull()) {
            if (!locatorElement.isJsonObject()) {
                throw new IllegalArgumentException("GEO locators are not an object");
            }
            if (locatorElement.getAsJsonObject().size() > remainingLocators) {
                throw new IllegalArgumentException("GEO locator count exceeds remaining budget");
            }
        }
        List<BedrockGeometry.Cube> cubes = parseCubes(
                cubeArray, remainingCubes
        );
        List<BedrockGeometry.Locator> locators = parseLocators(
                locatorElement, remainingLocators
        );
        return new BedrockGeometry.Bone(name, parent, pivot, rotation, cubes, locators);
    }

    private static List<BedrockGeometry.Cube> parseCubes(
            JsonArray array, int remainingCubes
    ) {
        if (array == null) {
            return List.of();
        }
        if (array.size() > remainingCubes) {
            throw new IllegalArgumentException("GEO cube count exceeds remaining budget");
        }
        List<BedrockGeometry.Cube> cubes = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("GEO cube is not an object");
            }
            JsonObject cube = element.getAsJsonObject();
            requireKeys(cube, CUBE_KEYS, "cube");
            BedrockGeometry.Vec3 origin = vector(cube, "origin", true);
            BedrockGeometry.Vec3 size = vector(cube, "size", true);
            BedrockGeometry.Vec3 pivot = vector(cube, "pivot", false);
            BedrockGeometry.Vec3 rotation = vector(cube, "rotation", false);
            if (rotation != null && pivot == null) {
                throw new IllegalArgumentException("rotated GEO cube lacks pivot");
            }
            JsonArray uv = array(cube, "uv", true);
            if (uv.size() != 2) {
                throw new IllegalArgumentException("GEO cube UV is not box UV");
            }
            int u = integer(uv.get(0), "cube u");
            int v = integer(uv.get(1), "cube v");
            float inflate = finiteNumber(cube, "inflate", false, 0F);
            boolean mirror = bool(cube, "mirror", false, false);
            cubes.add(new BedrockGeometry.Cube(
                    origin, size, pivot, rotation, u, v, inflate, mirror
            ));
        }
        return List.copyOf(cubes);
    }

    private static List<BedrockGeometry.Locator> parseLocators(
            JsonElement element, int remainingLocators
    ) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("GEO locators are not an object");
        }
        JsonObject object = element.getAsJsonObject();
        if (object.size() > remainingLocators) {
            throw new IllegalArgumentException("GEO locator count exceeds remaining budget");
        }
        List<BedrockGeometry.Locator> locators = new ArrayList<>(object.size());
        Set<String> names = new LinkedHashSet<>();
        for (var entry : object.entrySet()) {
            String name = boundedString(entry.getKey(), "locator name");
            if (!names.add(name)) {
                throw new IllegalArgumentException("duplicate locator name");
            }
            BedrockGeometry.Vec3 offset;
            BedrockGeometry.Vec3 rotation;
            if (entry.getValue().isJsonArray()) {
                offset = vector(entry.getValue(), "locator offset");
                rotation = zero();
            } else if (entry.getValue().isJsonObject()) {
                JsonObject locator = entry.getValue().getAsJsonObject();
                requireKeys(locator, LOCATOR_KEYS, "locator");
                offset = vectorOrDefault(locator, "offset", zero());
                rotation = vectorOrDefault(locator, "rotation", zero());
            } else {
                throw new IllegalArgumentException("invalid GEO locator");
            }
            locators.add(new BedrockGeometry.Locator(name, offset, rotation));
        }
        return List.copyOf(locators);
    }

    private static void requireKeys(JsonObject object, Set<String> allowed, String label) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("unsupported " + label + " key: " + key);
            }
        }
    }

    private static JsonObject object(JsonObject parent, String key, boolean required) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            if (required) {
                throw new IllegalArgumentException("missing GEO " + key);
            }
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("GEO " + key + " is not an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String key, boolean required) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            if (required) {
                throw new IllegalArgumentException("missing GEO " + key);
            }
            return null;
        }
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException("GEO " + key + " is not an array");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonObject parent, String key, boolean required) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            if (required) {
                throw new IllegalArgumentException("missing GEO " + key);
            }
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("GEO " + key + " is not a string");
        }
        return boundedString(value.getAsString(), key);
    }

    private static String boundedString(String value, String label) {
        byte[] bytes;
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("invalid GEO " + label, exception);
        }
        if (value.isEmpty() || bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("GEO " + label + " outside string budget");
        }
        return value;
    }

    private static int boundedInteger(
            JsonObject parent, String key, boolean required, int minimum, int maximum
    ) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            if (required) {
                throw new IllegalArgumentException("missing GEO " + key);
            }
            return 0;
        }
        int result = integer(value, key);
        if (result < minimum || result > maximum) {
            throw new IllegalArgumentException("GEO " + key + " outside integer budget");
        }
        return result;
    }

    private static int integer(JsonElement element, String label) {
        JsonPrimitive primitive = number(element, label);
        try {
            return new BigDecimal(primitive.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("GEO " + label + " is not an integer", exception);
        }
    }

    private static BedrockGeometry.Vec3 vector(
            JsonObject parent, String key, boolean required
    ) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            if (required) {
                throw new IllegalArgumentException("missing GEO " + key);
            }
            return null;
        }
        return vector(value, key);
    }

    private static BedrockGeometry.Vec3 vector(JsonElement element, String label) {
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException("GEO " + label + " is not a 3-vector");
        }
        JsonArray values = element.getAsJsonArray();
        return new BedrockGeometry.Vec3(
                finiteNumber(values.get(0), label),
                finiteNumber(values.get(1), label),
                finiteNumber(values.get(2), label)
        );
    }

    private static BedrockGeometry.Vec3 vectorOrDefault(
            JsonObject parent, String key, BedrockGeometry.Vec3 fallback
    ) {
        BedrockGeometry.Vec3 value = vector(parent, key, false);
        return value == null ? fallback : value;
    }

    private static void optionalVector(JsonObject parent, String key) {
        if (parent.has(key)) {
            vector(parent, key, true);
        }
    }

    private static void optionalFiniteNumber(JsonObject parent, String key) {
        if (parent.has(key)) {
            finiteNumber(parent, key, true, 0F);
        }
    }

    private static float finiteNumber(
            JsonObject parent, String key, boolean required, float fallback
    ) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            if (required) {
                throw new IllegalArgumentException("missing GEO " + key);
            }
            return fallback;
        }
        return finiteNumber(value, key);
    }

    private static float finiteNumber(JsonElement element, String label) {
        JsonPrimitive primitive = number(element, label);
        float value;
        try {
            value = Float.parseFloat(primitive.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("GEO " + label + " is not numeric", exception);
        }
        if (!Float.isFinite(value) || Math.abs(value) > MAX_ABSOLUTE_VALUE) {
            throw new IllegalArgumentException("GEO " + label + " outside numeric budget");
        }
        return value;
    }

    private static JsonPrimitive number(JsonElement element, String label) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("GEO " + label + " is not a number");
        }
        return element.getAsJsonPrimitive();
    }

    private static boolean bool(
            JsonObject parent, String key, boolean required, boolean fallback
    ) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            if (required) {
                throw new IllegalArgumentException("missing GEO " + key);
            }
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("GEO " + key + " is not boolean");
        }
        return value.getAsBoolean();
    }

    private static BedrockGeometry.Vec3 zero() {
        return new BedrockGeometry.Vec3(0F, 0F, 0F);
    }

    private static String strictUtf8(byte[] raw) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("invalid UTF-8 GEO", exception);
        }
    }
}
