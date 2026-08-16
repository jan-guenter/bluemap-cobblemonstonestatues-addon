/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.pose;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoseStateCodecTest {

    @Test
    void roundTripsRawMatrixNormalBitsVisibilityAndGeneratedChildPaths() {
        PoseState state = sampleState();
        byte[] encoded = PoseStateCodec.encode(state);
        PoseState decoded = PoseStateCodec.decode(encoded);

        assertEquals(state, decoded);
        assertArrayEquals(encoded, PoseStateCodec.encode(decoded));
        assertEquals(
                Float.floatToRawIntBits(-0.0F),
                Float.floatToRawIntBits(decoded.nodes().getFirst()
                        .runtimeFrame().positionTransform().m30())
        );
        assertEquals(
                Float.floatToRawIntBits(0.0F),
                Float.floatToRawIntBits(decoded.nodes().getFirst()
                        .runtimeFrame().cardinalNormals().northX())
        );
        assertEquals(
                Float.floatToRawIntBits(-0.0F),
                Float.floatToRawIntBits(decoded.nodes().getFirst()
                        .runtimeFrame().mirrorZeroXNormals().northX())
        );
        assertEquals(null, decoded.nodes().get(1).runtimeFrame());
        assertEquals("%25wing%251", PoseStateCodec.encodeSegment("%wing%1"));
        assertEquals(64, PoseStateCodec.sha256(encoded).length());
    }

    @Test
    void contentAddressChangesForOneRuntimeObservedMatrixBit() {
        PoseState first = sampleState();
        PoseState.Node original = first.nodes().getFirst();
        PoseState.PositionMatrix matrix = original.runtimeFrame().positionTransform();
        PoseState.PositionMatrix realizedAgain = new PoseState.PositionMatrix(
                matrix.m00(), matrix.m01() + 0.0001F, matrix.m02(), matrix.m03(),
                matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
                matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
                matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33()
        );
        PoseState.Node changed = new PoseState.Node(
                original.path(), original.parentIndex(), original.siblingOrdinal(),
                new PoseState.RuntimeFrame(
                        realizedAgain, original.runtimeFrame().cardinalNormals(),
                        original.runtimeFrame().mirrorZeroXNormals()
                ),
                original.visible(), original.skipDraw()
        );
        PoseState second = new PoseState(
                first.selectedRootPath(), first.tintArgb(),
                List.of(changed, first.nodes().get(1))
        );

        assertNotEquals(
                PoseStateCodec.sha256(PoseStateCodec.encode(first)),
                PoseStateCodec.sha256(PoseStateCodec.encode(second))
        );
    }

    @Test
    void contentAddressChangesForOnlyTheMirrorZeroSignBit() {
        PoseState first = sampleState();
        PoseState.Node original = first.nodes().getFirst();
        PoseState.RuntimeFrame frame = original.runtimeFrame();
        PoseState.MirrorZeroXNormals mirror = frame.mirrorZeroXNormals();
        PoseState.MirrorZeroXNormals positiveNorthZero =
                new PoseState.MirrorZeroXNormals(
                        mirror.downX(), mirror.downY(), mirror.downZ(),
                        mirror.upX(), mirror.upY(), mirror.upZ(),
                        0.0F, mirror.northY(), mirror.northZ(),
                        mirror.southX(), mirror.southY(), mirror.southZ()
                );
        PoseState.Node changed = new PoseState.Node(
                original.path(), original.parentIndex(), original.siblingOrdinal(),
                new PoseState.RuntimeFrame(
                        frame.positionTransform(), frame.cardinalNormals(),
                        positiveNorthZero
                ),
                original.visible(), original.skipDraw()
        );
        PoseState second = new PoseState(
                first.selectedRootPath(), first.tintArgb(),
                List.of(changed, first.nodes().get(1))
        );

        assertNotEquals(
                PoseStateCodec.sha256(PoseStateCodec.encode(first)),
                PoseStateCodec.sha256(PoseStateCodec.encode(second))
        );
    }

    @Test
    void rejectsTopologyPathFlagsTruncationAndTrailingBytes() {
        PoseState state = sampleState();
        assertThrows(IllegalArgumentException.class, () -> new PoseState(
                state.selectedRootPath(), state.tintArgb(),
                List.of(state.nodes().getFirst(), node(
                        "/altaria/tail", -1, 0, true, false
                ))
        ));
        assertThrows(IllegalArgumentException.class, () -> new PoseState(
                "/altaria", state.tintArgb(),
                List.of(state.nodes().get(0), state.nodes().get(1), state.nodes().get(1))
        ));
        assertThrows(IllegalArgumentException.class, () ->
                PoseStateCodec.encodeSegment(""));
        assertThrows(IllegalArgumentException.class, () ->
                PoseStateCodec.encodeSegment("\uD800"));
        PoseState wrapperSelected = new PoseState("/", state.tintArgb(), List.of(
                node("/", -1, 0, true, false),
                node("/altaria", 0, 0, true, false)
        ));
        assertEquals(wrapperSelected, PoseStateCodec.decode(
                PoseStateCodec.encode(wrapperSelected)
        ));
        assertThrows(IllegalArgumentException.class, () -> new PoseState.Node(
                "/%41", 0, 0, frame(), true, false
        ));

        PoseState.Node sibling = node("/altaria/sibling", 0, 1, true, false);
        PoseState.Node reenteredChild = node(
                "/altaria/%25wing%251/reentered", 1, 0, true, false
        );
        assertThrows(IllegalArgumentException.class, () -> new PoseState(
                state.selectedRootPath(), state.tintArgb(),
                List.of(state.nodes().get(0), state.nodes().get(1), sibling, reenteredChild)
        ));
        PoseState.Node ordinalGap = node("/altaria/sibling", 0, 2, true, false);
        assertThrows(IllegalArgumentException.class, () -> new PoseState(
                state.selectedRootPath(), state.tintArgb(),
                List.of(state.nodes().get(0), state.nodes().get(1), ordinalGap)
        ));

        byte[] encoded = PoseStateCodec.encode(state);
        byte[] truncated = java.util.Arrays.copyOf(encoded, encoded.length - 1);
        assertThrows(IllegalArgumentException.class, () -> PoseStateCodec.decode(truncated));
        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IllegalArgumentException.class, () -> PoseStateCodec.decode(trailing));

        byte[] oldFormatThree = encoded.clone();
        ByteBuffer.wrap(oldFormatThree).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x33535441)
                .putInt(3);
        assertThrows(
                IllegalArgumentException.class,
                () -> PoseStateCodec.decode(oldFormatThree)
        );

        byte[] badFlags = encoded.clone();
        ByteBuffer input = ByteBuffer.wrap(badFlags).order(ByteOrder.LITTLE_ENDIAN);
        input.position(16);
        skipString(input);
        skipNode(input);
        skipString(input);
        input.position(input.position() + 2 * Integer.BYTES);
        input.putInt(8);
        assertThrows(IllegalArgumentException.class, () -> PoseStateCodec.decode(badFlags));
    }

    @Test
    void rejectsProjectiveMatricesAndInvalidRuntimeNormals() {
        assertThrows(IllegalArgumentException.class, () -> new PoseState.PositionMatrix(
                1F, 0F, 0F, 0.25F,
                0F, 1F, 0F, 0F,
                0F, 0F, 1F, 0F,
                0F, 0F, 0F, 1F
        ));
        assertThrows(IllegalArgumentException.class, () -> new PoseState.CardinalNormals(
                0F, 0F, 0F, 0F, 1F, 0F,
                -1F, 0F, 0F, 0F, 0F, -1F,
                1F, 0F, 0F, 0F, 0F, 1F
        ));
        assertThrows(IllegalArgumentException.class, () ->
                new PoseState.MirrorZeroXNormals(
                        0F, 0F, 0F, -0.0F, 1F, 0F,
                        -0.0F, 0F, -1F, -0.0F, 0F, 1F
                ));
        assertThrows(IllegalArgumentException.class, () -> new PoseState.RuntimeFrame(
                identityMatrix(), identityNormals(), new PoseState.MirrorZeroXNormals(
                        -0.0F, -1F, 0F, -0.0F, 1F, 0F,
                        0.25F, 0F, -1F, -0.0F, 0F, 1F
                )
        ));
    }

    @Test
    void rejectsFramesOnHiddenDescendantsAndSkipDrawNodes() {
        PoseState.Node root = node("/root", -1, 0, true, false);
        assertThrows(IllegalArgumentException.class, () -> new PoseState(
                "/root", -1, List.of(root, new PoseState.Node(
                        "/root/skip", 0, 0, frame(), true, true
                ))
        ));
        PoseState.Node hidden = node("/root/hidden", 0, 0, false, false);
        assertThrows(IllegalArgumentException.class, () -> new PoseState(
                "/root", -1, List.of(root, hidden, new PoseState.Node(
                        "/root/hidden/child", 1, 0, frame(), true, false
                ))
        ));
    }

    private static PoseState sampleState() {
        PoseState.PositionMatrix root = new PoseState.PositionMatrix(
                1F, 0F, 0F, 0F,
                0F, 1F, 0F, 0F,
                0F, 0F, 1F, 0F,
                -0.0F, 1.25F, 0F, 1F
        );
        PoseState.CardinalNormals rootNormals = new PoseState.CardinalNormals(
                0F, -1F, 0F, 0F, 1F, 0F,
                -1F, 0F, 0F, 0.0F, 0F, -1F,
                1F, 0F, 0F, 0F, 0F, 1F
        );
        PoseState.MirrorZeroXNormals rootMirrorNormals = mirrorIdentityNormals();
        return new PoseState("/altaria", 0xffeeddcc, List.of(
                new PoseState.Node(
                        "/altaria", -1, 0,
                        new PoseState.RuntimeFrame(
                                root, rootNormals, rootMirrorNormals
                        ), true, false
                ),
                node("/altaria/%25wing%251", 0, 0, false, true)
        ));
    }

    private static PoseState.Node node(
            String path, int parent, int ordinal, boolean visible, boolean skipDraw
    ) {
        return new PoseState.Node(
                path, parent, ordinal, visible && !skipDraw ? frame() : null,
                visible, skipDraw
        );
    }

    private static PoseState.RuntimeFrame frame() {
        return new PoseState.RuntimeFrame(
                identityMatrix(), identityNormals(), mirrorIdentityNormals()
        );
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

    private static void skipString(ByteBuffer input) {
        int length = input.getInt();
        input.position(input.position() + length);
    }

    private static void skipNode(ByteBuffer input) {
        skipString(input);
        input.position(input.position() + 2 * Integer.BYTES);
        int flags = input.getInt();
        if ((flags & 4) != 0) {
            input.position(input.position() + 46 * Float.BYTES);
        }
    }
}
