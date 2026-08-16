/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelTreeCaptureTest {

    @Test
    void capturesSelectedSubtreeOrderAndOnlyDrawBearingRuntimeFrames() {
        FakeNode wrapper = emptyNode();
        FakeNode unused = drawNode(11F);
        FakeNode selected = drawNode(-0.0F);
        FakeNode hidden = node(false, false, false, 3F);
        FakeNode hiddenNested = drawNode(5F);
        FakeNode alpha = drawNode(7F);
        wrapper.children.put("unused", unused);
        wrapper.children.put("selected/%", selected);
        selected.children.put("zeta", hidden);
        selected.children.put("alpha", alpha);
        hidden.children.put("snowman-☃", hiddenNested);
        FakeAccess access = new FakeAccess();

        PoseState state = ModelTreeCapture.capture(wrapper, selected, access)
                .withTint(0x7F123456);

        assertEquals("/selected%2F%25", state.selectedRootPath());
        assertEquals(List.of(
                "/selected%2F%25",
                "/selected%2F%25/zeta",
                "/selected%2F%25/zeta/snowman-%E2%98%83",
                "/selected%2F%25/alpha"
        ), state.nodes().stream().map(PoseState.Node::path).toList());
        assertEquals(-1, state.nodes().get(0).parentIndex());
        assertEquals(0, state.nodes().get(1).parentIndex());
        assertEquals(1, state.nodes().get(2).parentIndex());
        assertEquals(0, state.nodes().get(1).siblingOrdinal());
        assertEquals(1, state.nodes().get(3).siblingOrdinal());
        assertEquals(Float.floatToRawIntBits(-0.0F), Float.floatToRawIntBits(
                state.nodes().getFirst().runtimeFrame().positionTransform().m30()
        ));
        assertNotNull(state.nodes().get(0).runtimeFrame());
        assertNull(state.nodes().get(1).runtimeFrame());
        assertNull(state.nodes().get(2).runtimeFrame());
        assertNotNull(state.nodes().get(3).runtimeFrame());
        assertFalse(state.nodes().stream().anyMatch(node -> node.path().contains("unused")));
        assertEquals(0x7F123456, state.tintArgb());
        assertEquals(0, access.depth);
        assertEquals(2, access.framesRead);
    }

    @Test
    void hiddenSkipDrawAndEmptyNodesAvoidUnusedNormalsButDrawNodeFailureIsFatal() {
        FakeNode root = emptyNode();
        FakeNode hidden = drawNode(0F);
        hidden.visible = false;
        hidden.frameFailure = true;
        FakeNode skip = drawNode(0F);
        skip.skipDraw = true;
        skip.frameFailure = true;
        FakeNode empty = emptyNode();
        empty.frameFailure = true;
        root.children.put("hidden", hidden);
        root.children.put("skip", skip);
        root.children.put("empty", empty);
        FakeAccess access = new FakeAccess();

        PoseState state = ModelTreeCapture.capture(root, root, access).withTint(-1);
        assertEquals(4, state.nodes().size());
        state.nodes().forEach(node -> assertNull(node.runtimeFrame()));
        assertEquals(0, access.framesRead);
        assertEquals(0, access.depth);

        FakeNode drawFailure = drawNode(0F);
        drawFailure.frameFailure = true;
        FakeAccess failingAccess = new FakeAccess();
        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(drawFailure, drawFailure, failingAccess));
        assertEquals(0, failingAccess.depth);
    }

    @Test
    void exactZeroScaleSuppressesOwnAndDescendantCubesWithoutLosingTreeClosure() {
        FakeNode root = emptyNode();
        root.zeroScale = true;
        FakeNode draw = drawNode(1F);
        FakeNode nested = drawNode(2F);
        draw.nonFiniteFrame = true;
        nested.nonFiniteFrame = true;
        FakeNode explicitSkip = drawNode(3F);
        explicitSkip.skipDraw = true;
        root.children.put("draw", draw);
        draw.children.put("nested", nested);
        nested.children.put("explicit-skip", explicitSkip);
        FakeAccess access = new FakeAccess();

        PoseState state = ModelTreeCapture.capture(root, root, access).withTint(-1);

        assertEquals(List.of("/", "/draw", "/draw/nested",
                "/draw/nested/explicit-skip"), state.nodes().stream()
                .map(PoseState.Node::path).toList());
        assertFalse(state.nodes().get(0).skipDraw());
        assertEquals(List.of(true, true, true), state.nodes().subList(1, 4).stream()
                .map(PoseState.Node::skipDraw).toList());
        state.nodes().forEach(node -> assertNull(node.runtimeFrame()));
        assertEquals(2, access.suppressedDraws);
        assertEquals(2, access.framesRead);
        assertEquals(0, access.depth);
    }

    @Test
    void uniformZeroScaleWithFiniteNormalsRemainsAnOrdinaryCapturedFrame() {
        FakeNode draw = drawNode(1F);
        draw.zeroScale = true;
        FakeAccess access = new FakeAccess();

        PoseState state = ModelTreeCapture.capture(draw, draw, access).withTint(-1);

        assertNotNull(state.nodes().getFirst().runtimeFrame());
        assertFalse(state.nodes().getFirst().skipDraw());
        assertEquals(1, access.framesRead);
        assertEquals(0, access.suppressedDraws);
    }

    @Test
    void nonFiniteFrameWithoutExactZeroScaleIsFatal() {
        FakeNode draw = drawNode(1F);
        draw.nonFiniteFrame = true;

        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(draw, draw, new FakeAccess()));
    }

    @Test
    void visibleEmptyLeafMatchesModelPartEarlyReturnWithoutEnteringTransform() {
        FakeNode leaf = emptyNode();
        leaf.zeroScale = true;
        FakeAccess access = new FakeAccess();

        PoseState state = ModelTreeCapture.capture(leaf, leaf, access).withTint(-1);

        assertEquals(1, state.nodes().size());
        assertNull(state.nodes().getFirst().runtimeFrame());
        assertEquals(0, access.enters);
        assertEquals(0, access.depth);
    }

    @Test
    void zeroScaleOutsideSelectedRootCannotSuppressTheSelectedSubtree() {
        FakeNode wrapper = emptyNode();
        wrapper.zeroScale = true;
        FakeNode selected = drawNode(1F);
        selected.nonFiniteFrame = true;
        wrapper.children.put("selected", selected);
        FakeAccess access = new FakeAccess();

        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(wrapper, selected, access));
        assertEquals(0, access.suppressedDraws);
        assertEquals(0, access.depth);
    }

    @Test
    void supportsSelectingTheWrapperRoot() {
        FakeNode wrapper = emptyNode();
        wrapper.children.put("right", drawNode(1F));
        wrapper.children.put("left", drawNode(2F));

        PoseState state = ModelTreeCapture.capture(wrapper, wrapper, new FakeAccess())
                .withTint(0xFFFFFFFF);
        assertEquals("/", state.selectedRootPath());
        assertEquals(List.of("/", "/right", "/left"), state.nodes().stream()
                .map(PoseState.Node::path).toList());
    }

    @Test
    void rejectsMissingAliasedCyclicMalformedAndUnsupportedTrees() {
        FakeNode wrapper = emptyNode();
        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(wrapper, emptyNode(), new FakeAccess()));

        FakeNode alias = emptyNode();
        wrapper.children.put("first", alias);
        wrapper.children.put("second", alias);
        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(wrapper, alias, new FakeAccess()));

        FakeNode cyclic = emptyNode();
        cyclic.children.put("self", cyclic);
        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(cyclic, cyclic, new FakeAccess()));

        FakeNode emptyName = emptyNode();
        emptyName.children.put("", emptyNode());
        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(emptyName, emptyName, new FakeAccess()));

        FakeNode nullChild = emptyNode();
        nullChild.children.put("null", null);
        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(nullChild, nullChild, new FakeAccess()));

        FakeNode unsupported = emptyNode();
        unsupported.supported = false;
        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(unsupported, unsupported, new FakeAccess()));

        FakeNode deep = emptyNode();
        FakeNode cursor = deep;
        for (int index = 0; index < 513; index++) {
            FakeNode child = emptyNode();
            cursor.children.put("n" + index, child);
            cursor = child;
        }
        assertThrows(IllegalArgumentException.class, () ->
                ModelTreeCapture.capture(deep, deep, new FakeAccess()));
    }

    private static FakeNode emptyNode() {
        return node(true, false, true, 0F);
    }

    private static FakeNode drawNode(float translation) {
        return node(true, false, false, translation);
    }

    private static FakeNode node(
            boolean visible,
            boolean skipDraw,
            boolean empty,
            float translation
    ) {
        return new FakeNode(visible, skipDraw, empty, frame(translation));
    }

    private static PoseState.RuntimeFrame frame(float translation) {
        return new PoseState.RuntimeFrame(
                new PoseState.PositionMatrix(
                        1F, 0F, 0F, 0F,
                        0F, 1F, 0F, 0F,
                        0F, 0F, 1F, 0F,
                        translation, 0F, 0F, 1F
                ),
                new PoseState.CardinalNormals(
                        0F, -1F, 0F, 0F, 1F, 0F,
                        -1F, 0F, 0F, 0F, 0F, -1F,
                        1F, 0F, 0F, 0F, 0F, 1F
                ),
                new PoseState.MirrorZeroXNormals(
                        -0.0F, -1F, 0F, -0.0F, 1F, 0F,
                        -0.0F, 0F, -1F, -0.0F, 0F, 1F
                )
        );
    }

    private static final class FakeAccess
            implements ModelTreeCapture.NodeAccess<FakeNode> {
        private int depth;
        private int enters;
        private int framesRead;
        private int suppressedDraws;

        @Override
        public Map<String, FakeNode> children(FakeNode node) {
            if (!node.supported) {
                throw new IllegalArgumentException("unsupported node");
            }
            return node.children;
        }

        @Override
        public ModelTreeCapture.RenderFlags flags(FakeNode node) {
            return new ModelTreeCapture.RenderFlags(
                    node.visible, node.skipDraw, node.empty
            );
        }

        @Override
        public boolean enter(FakeNode node) {
            enters++;
            depth++;
            return node.zeroScale;
        }

        @Override
        public ModelTreeCapture.FrameDecision frame(
                FakeNode node, boolean zeroScaleInPath
        ) {
            framesRead++;
            if (node.nonFiniteFrame) {
                if (zeroScaleInPath) {
                    return ModelTreeCapture.FrameDecision.suppress();
                }
                throw new IllegalArgumentException(
                        "non-finite normal without exact zero scale"
                );
            }
            if (node.frameFailure) {
                throw new IllegalArgumentException("invalid transformed normal");
            }
            return ModelTreeCapture.FrameDecision.captured(node.frame);
        }

        @Override
        public void suppressDraw(FakeNode node) {
            suppressedDraws++;
            node.skipDraw = true;
        }

        @Override
        public void exit(FakeNode node) {
            depth--;
        }
    }

    private static final class FakeNode {
        private final Map<String, FakeNode> children = new LinkedHashMap<>();
        private final boolean empty;
        private final PoseState.RuntimeFrame frame;
        private boolean visible;
        private boolean skipDraw;
        private boolean supported = true;
        private boolean frameFailure;
        private boolean nonFiniteFrame;
        private boolean zeroScale;

        private FakeNode(
                boolean visible,
                boolean skipDraw,
                boolean empty,
                PoseState.RuntimeFrame frame
        ) {
            this.visible = visible;
            this.skipDraw = skipDraw;
            this.empty = empty;
            this.frame = frame;
        }
    }
}
