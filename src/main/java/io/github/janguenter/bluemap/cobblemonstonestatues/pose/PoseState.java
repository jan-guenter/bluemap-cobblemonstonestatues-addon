/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.pose;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable runtime-observed absolute render state for one exact named model subtree. */
public record PoseState(String selectedRootPath, int tintArgb, List<Node> nodes) {

    public static final int MAX_NODES = 50_000;
    static final int MAX_PATH_BYTES = 4_096;
    private static final float MAX_TRANSFORM_VALUE = 1_000_000F;

    public PoseState {
        requireCanonicalPath(selectedRootPath, "selected root path");
        nodes = List.copyOf(nodes);
        if (nodes.isEmpty() || nodes.size() > MAX_NODES) {
            throw new IllegalArgumentException("pose-state node count outside budget");
        }
        if (!selectedRootPath.equals(nodes.getFirst().path())
                || nodes.getFirst().parentIndex() != -1
                || nodes.getFirst().siblingOrdinal() != 0) {
            throw new IllegalArgumentException("pose-state lacks its selected rendered root");
        }

        Map<String, Integer> paths = new HashMap<>();
        Map<Integer, Integer> nextOrdinal = new HashMap<>();
        Deque<Integer> openAncestors = new ArrayDeque<>();
        boolean[] effectiveVisibility = new boolean[nodes.size()];
        boolean selectedRootFound = false;
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (paths.putIfAbsent(node.path(), index) != null) {
                throw new IllegalArgumentException("duplicate pose-state node path");
            }
            if (node.path().equals(selectedRootPath)) {
                selectedRootFound = true;
            }
            if (index == 0) {
                if (node.siblingOrdinal() != 0) {
                    throw new IllegalArgumentException("invalid selected-root ordinal");
                }
                effectiveVisibility[index] = node.visible();
                requireFrameVisibility(node, effectiveVisibility[index]);
                openAncestors.addLast(index);
                continue;
            }
            if (node.parentIndex() < 0 || node.parentIndex() >= index) {
                throw new IllegalArgumentException("pose-state parent is not earlier in pre-order");
            }
            while (!openAncestors.isEmpty()
                    && openAncestors.getLast() != node.parentIndex()) {
                openAncestors.removeLast();
            }
            if (openAncestors.isEmpty()) {
                throw new IllegalArgumentException("pose-state nodes are not contiguous pre-order");
            }
            String expectedParent = parentPath(node.path());
            if (!nodes.get(node.parentIndex()).path().equals(expectedParent)
                    || !"/".equals(selectedRootPath)
                    && !node.path().startsWith(selectedRootPath + "/")) {
                throw new IllegalArgumentException("pose-state path does not match its parent");
            }
            int expectedOrdinal = nextOrdinal.getOrDefault(node.parentIndex(), 0);
            if (node.siblingOrdinal() != expectedOrdinal) {
                throw new IllegalArgumentException("noncanonical pose-state sibling ordinal");
            }
            nextOrdinal.put(node.parentIndex(), expectedOrdinal + 1);
            effectiveVisibility[index] = effectiveVisibility[node.parentIndex()]
                    && node.visible();
            requireFrameVisibility(node, effectiveVisibility[index]);
            openAncestors.addLast(index);
        }
        if (!selectedRootFound) {
            throw new IllegalArgumentException("selected root is absent from pose-state tree");
        }
    }

    public record Node(
            String path,
            int parentIndex,
            int siblingOrdinal,
            RuntimeFrame runtimeFrame,
            boolean visible,
            boolean skipDraw
    ) {
        public Node {
            requireCanonicalPath(path, "node path");
            if (parentIndex < -1 || siblingOrdinal < 0) {
                throw new IllegalArgumentException("invalid pose-state tree index");
            }
        }
    }

    /** Absolute runtime transform present only for a draw-bearing, effectively visible node. */
    public record RuntimeFrame(
            PositionMatrix positionTransform,
            CardinalNormals cardinalNormals,
            MirrorZeroXNormals mirrorZeroXNormals
    ) {
        public RuntimeFrame {
            Objects.requireNonNull(positionTransform, "positionTransform");
            Objects.requireNonNull(cardinalNormals, "cardinalNormals");
            Objects.requireNonNull(mirrorZeroXNormals, "mirrorZeroXNormals");
            requireMirrorNumericEquality(cardinalNormals, mirrorZeroXNormals);
        }
    }

    /** All sixteen raw coefficients of the runtime PoseStack position matrix. */
    public record PositionMatrix(
            float m00, float m01, float m02, float m03,
            float m10, float m11, float m12, float m13,
            float m20, float m21, float m22, float m23,
            float m30, float m31, float m32, float m33
    ) {
        public PositionMatrix {
            requireFiniteBounded(m00, "matrix m00");
            requireFiniteBounded(m01, "matrix m01");
            requireFiniteBounded(m02, "matrix m02");
            requireFiniteBounded(m03, "matrix m03");
            requireFiniteBounded(m10, "matrix m10");
            requireFiniteBounded(m11, "matrix m11");
            requireFiniteBounded(m12, "matrix m12");
            requireFiniteBounded(m13, "matrix m13");
            requireFiniteBounded(m20, "matrix m20");
            requireFiniteBounded(m21, "matrix m21");
            requireFiniteBounded(m22, "matrix m22");
            requireFiniteBounded(m23, "matrix m23");
            requireFiniteBounded(m30, "matrix m30");
            requireFiniteBounded(m31, "matrix m31");
            requireFiniteBounded(m32, "matrix m32");
            requireFiniteBounded(m33, "matrix m33");
            if (m03 != 0F || m13 != 0F || m23 != 0F
                    || Float.floatToRawIntBits(m33) != Float.floatToRawIntBits(1F)) {
                throw new IllegalArgumentException("pose-state position matrix is not affine");
            }
        }
    }

    /** Six runtime-transformed cardinal normals in DOWN, UP, WEST, NORTH, EAST, SOUTH order. */
    public record CardinalNormals(
            float downX, float downY, float downZ,
            float upX, float upY, float upZ,
            float westX, float westY, float westZ,
            float northX, float northY, float northZ,
            float eastX, float eastY, float eastZ,
            float southX, float southY, float southZ
    ) {
        public CardinalNormals {
            requireFiniteBounded(downX, "down normal x");
            requireFiniteBounded(downY, "down normal y");
            requireFiniteBounded(downZ, "down normal z");
            requireFiniteBounded(upX, "up normal x");
            requireFiniteBounded(upY, "up normal y");
            requireFiniteBounded(upZ, "up normal z");
            requireFiniteBounded(westX, "west normal x");
            requireFiniteBounded(westY, "west normal y");
            requireFiniteBounded(westZ, "west normal z");
            requireFiniteBounded(northX, "north normal x");
            requireFiniteBounded(northY, "north normal y");
            requireFiniteBounded(northZ, "north normal z");
            requireFiniteBounded(eastX, "east normal x");
            requireFiniteBounded(eastY, "east normal y");
            requireFiniteBounded(eastZ, "east normal z");
            requireFiniteBounded(southX, "south normal x");
            requireFiniteBounded(southY, "south normal y");
            requireFiniteBounded(southZ, "south normal z");
            requireNormal(downX, downY, downZ, "down");
            requireNormal(upX, upY, upZ, "up");
            requireNormal(westX, westY, westZ, "west");
            requireNormal(northX, northY, northZ, "north");
            requireNormal(eastX, eastY, eastZ, "east");
            requireNormal(southX, southY, southZ, "south");
        }
    }

    /**
     * Runtime-transformed non-X face normals whose local X component is {@code -0.0f}.
     *
     * <p>Minecraft's mirrored cube path negates the local X component for every polygon normal.
     * For DOWN, UP, NORTH, and SOUTH that operation changes only the raw sign bit of zero, which
     * can survive a runtime transform and is part of the strict quad identity. Mirrored WEST and
     * EAST remain the ordinary cardinal inputs after the mirrored endpoint/face swap.</p>
     */
    public record MirrorZeroXNormals(
            float downX, float downY, float downZ,
            float upX, float upY, float upZ,
            float northX, float northY, float northZ,
            float southX, float southY, float southZ
    ) {
        public MirrorZeroXNormals {
            requireFiniteBounded(downX, "mirror-zero-X down normal x");
            requireFiniteBounded(downY, "mirror-zero-X down normal y");
            requireFiniteBounded(downZ, "mirror-zero-X down normal z");
            requireFiniteBounded(upX, "mirror-zero-X up normal x");
            requireFiniteBounded(upY, "mirror-zero-X up normal y");
            requireFiniteBounded(upZ, "mirror-zero-X up normal z");
            requireFiniteBounded(northX, "mirror-zero-X north normal x");
            requireFiniteBounded(northY, "mirror-zero-X north normal y");
            requireFiniteBounded(northZ, "mirror-zero-X north normal z");
            requireFiniteBounded(southX, "mirror-zero-X south normal x");
            requireFiniteBounded(southY, "mirror-zero-X south normal y");
            requireFiniteBounded(southZ, "mirror-zero-X south normal z");
            requireNormal(downX, downY, downZ, "mirror-zero-X down");
            requireNormal(upX, upY, upZ, "mirror-zero-X up");
            requireNormal(northX, northY, northZ, "mirror-zero-X north");
            requireNormal(southX, southY, southZ, "mirror-zero-X south");
        }
    }

    static void requireCanonicalPath(String value, String label) {
        Objects.requireNonNull(value, label);
        byte[] utf8 = PoseStateCodec.strictUtf8(value, label);
        if (utf8.length < 1 || utf8.length > MAX_PATH_BYTES || !value.startsWith("/")
                || value.endsWith("/") && value.length() > 1 || value.contains("//")) {
            throw new IllegalArgumentException("invalid " + label);
        }
        if ("/".equals(value)) {
            return;
        }
        for (String segment : value.substring(1).split("/", -1)) {
            if (!canonicalSegment(segment)) {
                throw new IllegalArgumentException("invalid " + label);
            }
        }
    }

    private static boolean canonicalSegment(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        byte[] decodedBytes = new byte[segment.length()];
        int decodedLength = 0;
        for (int index = 0; index < segment.length(); index++) {
            char value = segment.charAt(index);
            if (isUnreserved(value)) {
                decodedBytes[decodedLength++] = (byte) value;
                continue;
            }
            if (value != '%' || index + 2 >= segment.length()
                    || !upperHex(segment.charAt(index + 1))
                    || !upperHex(segment.charAt(index + 2))) {
                return false;
            }
            decodedBytes[decodedLength++] = (byte) ((hexValue(segment.charAt(index + 1)) << 4)
                    | hexValue(segment.charAt(index + 2)));
            index += 2;
        }
        try {
            String decoded = PoseStateCodec.strictUtf8Decode(
                    java.util.Arrays.copyOf(decodedBytes, decodedLength), "path segment"
            );
            return !decoded.isEmpty() && PoseStateCodec.encodeSegment(decoded).equals(segment);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isUnreserved(char value) {
        return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9' || value == '.' || value == '_'
                || value == '-' || value == '~';
    }

    private static boolean upperHex(char value) {
        return value >= '0' && value <= '9' || value >= 'A' && value <= 'F';
    }

    private static int hexValue(char value) {
        return value <= '9' ? value - '0' : value - 'A' + 10;
    }

    private static String parentPath(String path) {
        int separator = path.lastIndexOf('/');
        return separator == 0 ? "/" : path.substring(0, separator);
    }

    private static void requireFiniteBounded(float value, String label) {
        if (!Float.isFinite(value) || Math.abs(value) > MAX_TRANSFORM_VALUE) {
            throw new IllegalArgumentException("invalid pose-state " + label);
        }
    }

    private static void requireFrameVisibility(Node node, boolean effectiveVisible) {
        if (node.runtimeFrame() != null && (!effectiveVisible || node.skipDraw())) {
            throw new IllegalArgumentException(
                    "pose-state runtime frame belongs to a nondrawing node"
            );
        }
    }

    private static void requireNormal(float x, float y, float z, String label) {
        double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        if (length < 0.5D || length > 1.5D) {
            throw new IllegalArgumentException("invalid pose-state " + label + " normal");
        }
    }

    private static void requireMirrorNumericEquality(
            CardinalNormals ordinary, MirrorZeroXNormals mirror
    ) {
        if (ordinary.downX() != mirror.downX()
                || ordinary.downY() != mirror.downY()
                || ordinary.downZ() != mirror.downZ()
                || ordinary.upX() != mirror.upX()
                || ordinary.upY() != mirror.upY()
                || ordinary.upZ() != mirror.upZ()
                || ordinary.northX() != mirror.northX()
                || ordinary.northY() != mirror.northY()
                || ordinary.northZ() != mirror.northZ()
                || ordinary.southX() != mirror.southX()
                || ordinary.southY() != mirror.southY()
                || ordinary.southZ() != mirror.southZ()) {
            throw new IllegalArgumentException(
                    "mirror-zero-X normals differ numerically from ordinary cardinals"
            );
        }
    }
}
