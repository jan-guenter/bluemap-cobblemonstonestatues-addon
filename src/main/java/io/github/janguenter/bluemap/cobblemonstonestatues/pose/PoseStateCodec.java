/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.pose;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Canonical little-endian format-4 absolute runtime-transform codec. */
public final class PoseStateCodec {

    public static final int MAX_BYTES = 16 * 1024 * 1024;
    static final int MAGIC = 0x34535441; // ATS4 as little-endian bytes
    public static final int VERSION = 4;
    private static final int FIXED_HEADER_BYTES = 4 * Integer.BYTES;
    private static final int FRAME_FLOATS = 46;
    private static final int FIXED_NODE_BYTES = 4 * Integer.BYTES;

    private PoseStateCodec() {
    }

    public static byte[] encode(PoseState state) {
        byte[] selectedRoot = utf8(state.selectedRootPath());
        int size = Math.addExact(
                FIXED_HEADER_BYTES, Math.addExact(Integer.BYTES, selectedRoot.length)
        );
        for (PoseState.Node node : state.nodes()) {
            size = Math.addExact(size, Math.addExact(FIXED_NODE_BYTES, utf8(node.path()).length));
            if (node.runtimeFrame() != null) {
                size = Math.addExact(size, FRAME_FLOATS * Float.BYTES);
            }
        }
        if (size > MAX_BYTES) {
            throw new IllegalArgumentException("pose-state encoding exceeds byte budget");
        }
        ByteBuffer output = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(MAGIC).putInt(VERSION).putInt(state.tintArgb());
        output.putInt(state.nodes().size());
        putString(output, selectedRoot);
        for (PoseState.Node node : state.nodes()) {
            putString(output, utf8(node.path()));
            output.putInt(node.parentIndex()).putInt(node.siblingOrdinal());
            int flags = (node.visible() ? 1 : 0) | (node.skipDraw() ? 2 : 0)
                    | (node.runtimeFrame() != null ? 4 : 0);
            output.putInt(flags);
            if (node.runtimeFrame() != null) {
                put(output, node.runtimeFrame().positionTransform());
                put(output, node.runtimeFrame().cardinalNormals());
                put(output, node.runtimeFrame().mirrorZeroXNormals());
            }
        }
        return output.array();
    }

    public static PoseState decode(byte[] raw) {
        if (raw.length < FIXED_HEADER_BYTES || raw.length > MAX_BYTES) {
            throw new IllegalArgumentException("pose-state bytes outside budget");
        }
        try {
            ByteBuffer input = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            if (input.getInt() != MAGIC || input.getInt() != VERSION) {
                throw new IllegalArgumentException("unsupported pose-state header");
            }
            int tintArgb = input.getInt();
            int count = input.getInt();
            if (count < 1 || count > PoseState.MAX_NODES) {
                throw new IllegalArgumentException("pose-state node count outside budget");
            }
            String selectedRoot = getString(input);
            List<PoseState.Node> nodes = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String path = getString(input);
                int parentIndex = input.getInt();
                int siblingOrdinal = input.getInt();
                int flags = input.getInt();
                if ((flags & ~7) != 0) {
                    throw new IllegalArgumentException("unknown pose-state node flags");
                }
                PoseState.RuntimeFrame frame = null;
                if ((flags & 4) != 0) {
                    frame = new PoseState.RuntimeFrame(
                            getPositionMatrix(input), getCardinalNormals(input),
                            getMirrorZeroXNormals(input)
                    );
                }
                nodes.add(new PoseState.Node(
                        path, parentIndex, siblingOrdinal, frame,
                        (flags & 1) != 0, (flags & 2) != 0
                ));
            }
            if (input.hasRemaining()) {
                throw new IllegalArgumentException("trailing pose-state bytes");
            }
            PoseState state = new PoseState(selectedRoot, tintArgb, nodes);
            if (!java.util.Arrays.equals(raw, encode(state))) {
                throw new IllegalArgumentException("noncanonical pose-state encoding");
            }
            return state;
        } catch (java.nio.BufferUnderflowException exception) {
            throw new IllegalArgumentException("truncated pose-state encoding", exception);
        }
    }

    public static String sha256(byte[] raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void put(ByteBuffer output, PoseState.PositionMatrix value) {
        output.putFloat(value.m00()).putFloat(value.m01())
                .putFloat(value.m02()).putFloat(value.m03());
        output.putFloat(value.m10()).putFloat(value.m11())
                .putFloat(value.m12()).putFloat(value.m13());
        output.putFloat(value.m20()).putFloat(value.m21())
                .putFloat(value.m22()).putFloat(value.m23());
        output.putFloat(value.m30()).putFloat(value.m31())
                .putFloat(value.m32()).putFloat(value.m33());
    }

    private static void put(ByteBuffer output, PoseState.CardinalNormals value) {
        output.putFloat(value.downX()).putFloat(value.downY()).putFloat(value.downZ());
        output.putFloat(value.upX()).putFloat(value.upY()).putFloat(value.upZ());
        output.putFloat(value.westX()).putFloat(value.westY()).putFloat(value.westZ());
        output.putFloat(value.northX()).putFloat(value.northY()).putFloat(value.northZ());
        output.putFloat(value.eastX()).putFloat(value.eastY()).putFloat(value.eastZ());
        output.putFloat(value.southX()).putFloat(value.southY()).putFloat(value.southZ());
    }

    private static void put(ByteBuffer output, PoseState.MirrorZeroXNormals value) {
        output.putFloat(value.downX()).putFloat(value.downY()).putFloat(value.downZ());
        output.putFloat(value.upX()).putFloat(value.upY()).putFloat(value.upZ());
        output.putFloat(value.northX()).putFloat(value.northY()).putFloat(value.northZ());
        output.putFloat(value.southX()).putFloat(value.southY()).putFloat(value.southZ());
    }

    private static PoseState.PositionMatrix getPositionMatrix(ByteBuffer input) {
        return new PoseState.PositionMatrix(
                input.getFloat(), input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat(), input.getFloat()
        );
    }

    private static PoseState.CardinalNormals getCardinalNormals(ByteBuffer input) {
        return new PoseState.CardinalNormals(
                input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat()
        );
    }

    private static PoseState.MirrorZeroXNormals getMirrorZeroXNormals(ByteBuffer input) {
        return new PoseState.MirrorZeroXNormals(
                input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat(),
                input.getFloat(), input.getFloat(), input.getFloat()
        );
    }

    /** Percent-encodes a raw model child name into one canonical path segment. */
    public static String encodeSegment(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("empty model child name");
        }
        byte[] bytes = strictUtf8(raw, "model child name");
        StringBuilder output = new StringBuilder(bytes.length);
        for (byte element : bytes) {
            int value = element & 0xFF;
            if (unreserved(value)) {
                output.append((char) value);
            } else {
                output.append('%');
                output.append(Character.toUpperCase(Character.forDigit(value >>> 4, 16)));
                output.append(Character.toUpperCase(Character.forDigit(value & 15, 16)));
            }
        }
        return output.toString();
    }

    private static boolean unreserved(int value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9' || value == '.' || value == '_'
                || value == '-' || value == '~';
    }

    private static byte[] utf8(String value) {
        byte[] encoded = strictUtf8(value, "pose-state path");
        if (encoded.length < 1 || encoded.length > PoseState.MAX_PATH_BYTES) {
            throw new IllegalArgumentException("pose-state path bytes outside budget");
        }
        return encoded;
    }

    private static void putString(ByteBuffer output, byte[] value) {
        output.putInt(value.length).put(value);
    }

    private static String getString(ByteBuffer input) {
        int length = input.getInt();
        if (length < 1 || length > PoseState.MAX_PATH_BYTES || length > input.remaining()) {
            throw new IllegalArgumentException("pose-state path bytes outside budget");
        }
        byte[] value = new byte[length];
        input.get(value);
        return strictUtf8Decode(value, "pose-state path");
    }

    static byte[] strictUtf8(String value, String label) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("invalid UTF-16 " + label, exception);
        }
    }

    static String strictUtf8Decode(byte[] value, String label) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("invalid UTF-8 " + label, exception);
        }
    }
}
