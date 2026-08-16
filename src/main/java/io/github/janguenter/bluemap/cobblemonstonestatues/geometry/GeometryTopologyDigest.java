/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Order-neutral canonical identity for one parsed and baked exact GEO resource. */
public final class GeometryTopologyDigest {

    private static final long MAX_CANONICAL_BYTES = 128L * 1024L * 1024L;

    private GeometryTopologyDigest() {
    }

    public static String sha256(BakedGeometryTopology topology) {
        CanonicalHash output = new CanonicalHash();
        output.text("atmons-stone-geo-topology-v1");
        output.text(topology.sourceFormatVersion());
        output.text(topology.identifier());
        output.integer(topology.textureWidth());
        output.integer(topology.textureHeight());
        output.integer(topology.sourceBoneCount());
        output.integer(topology.sourceCubeCount());
        output.integer(topology.sourceLocatorCount());
        List<BakedGeometryTopology.Node> nodes = topology.nodes().stream()
                .sorted(Comparator.comparing(BakedGeometryTopology.Node::path)).toList();
        output.integer(nodes.size());
        for (BakedGeometryTopology.Node node : nodes) {
            output.text(node.name());
            output.text(node.path());
            output.nullableText(node.parentPath());
            transform(output, node.defaultTransform());
            output.bool(true);
            output.bool(false);
            output.integer(node.boxes().size());
            for (int ordinal = 0; ordinal < node.boxes().size(); ordinal++) {
                BakedGeometryTopology.Box box = node.boxes().get(ordinal);
                output.integer(ordinal);
                vector(output, box.origin());
                vector(output, box.size());
                output.integer(box.u());
                output.integer(box.v());
                output.floating(box.inflate());
                output.bool(box.mirror());
            }
        }
        return output.finish();
    }

    private static void transform(
            CanonicalHash output, BakedGeometryTopology.Transform transform
    ) {
        vector(output, transform.position());
        vector(output, transform.rotationRadians());
        vector(output, transform.scale());
    }

    private static void vector(CanonicalHash output, BedrockGeometry.Vec3 value) {
        output.floating(value.x());
        output.floating(value.y());
        output.floating(value.z());
    }

    private static final class CanonicalHash {

        private final MessageDigest digest;
        private long bytes;

        private CanonicalHash() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 unavailable", exception);
            }
        }

        private void nullableText(String value) {
            bool(value != null);
            if (value != null) {
                text(value);
            }
        }

        private void text(String value) {
            byte[] encoded = utf8(value);
            integer(encoded.length);
            update(encoded);
        }

        private void floating(float value) {
            integer(Float.floatToRawIntBits(value));
        }

        private void bool(boolean value) {
            update(new byte[]{(byte) (value ? 1 : 0)});
        }

        private void integer(int value) {
            update(new byte[]{
                    (byte) value,
                    (byte) (value >>> 8),
                    (byte) (value >>> 16),
                    (byte) (value >>> 24)
            });
        }

        private void update(byte[] value) {
            bytes = Math.addExact(bytes, value.length);
            if (bytes > MAX_CANONICAL_BYTES) {
                throw new IllegalArgumentException("canonical GEO topology exceeds byte budget");
            }
            digest.update(value);
        }

        private String finish() {
            if (bytes < 1) {
                throw new IllegalStateException("empty canonical GEO topology");
            }
            return HexFormat.of().formatHex(digest.digest());
        }

        private static byte[] utf8(String value) {
            try {
                ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .encode(CharBuffer.wrap(value));
                byte[] result = new byte[encoded.remaining()];
                encoded.get(result);
                return result;
            } catch (CharacterCodingException exception) {
                throw new IllegalArgumentException("invalid GEO topology text", exception);
            }
        }
    }
}
