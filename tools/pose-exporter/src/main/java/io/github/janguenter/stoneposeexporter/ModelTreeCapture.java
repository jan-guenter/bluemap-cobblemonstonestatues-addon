/* SPDX-License-Identifier: MIT */
package io.github.janguenter.stoneposeexporter;

import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Identity-safe named-tree capture core, independent of client runtime classes. */
final class ModelTreeCapture {

    private static final int MAX_PATH_BYTES = 4_096;
    private static final int MAX_DEPTH = 512;

    private ModelTreeCapture() {
    }

    static <N> CapturedTree capture(N wrapper, N selectedRoot, NodeAccess<N> access) {
        Objects.requireNonNull(wrapper, "wrapper");
        Objects.requireNonNull(selectedRoot, "selectedRoot");
        Objects.requireNonNull(access, "access");
        IdentityHashMap<N, ScannedNode<N>> scanned = new IdentityHashMap<>();
        scan(wrapper, "/", 0, access, scanned);
        ScannedNode<N> selected = scanned.get(selectedRoot);
        if (selected == null) {
            throw new IllegalArgumentException("selected model root is absent from wrapper tree");
        }
        List<PoseState.Node> nodes = new ArrayList<>();
        append(selected, -1, 0, true, false, access, scanned, nodes);
        return new CapturedTree(selected.path, nodes);
    }

    private static <N> void scan(
            N node,
            String path,
            int depth,
            NodeAccess<N> access,
            IdentityHashMap<N, ScannedNode<N>> scanned
    ) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("model wrapper tree exceeds depth budget");
        }
        if (scanned.size() >= PoseState.MAX_NODES) {
            throw new IllegalArgumentException("model wrapper tree exceeds node budget");
        }
        if (scanned.containsKey(node)) {
            throw new IllegalArgumentException("model wrapper tree has a cycle or identity alias");
        }
        Map<String, N> children = Objects.requireNonNull(
                access.children(node), "model node children"
        );
        List<Child<N>> snapshot = new ArrayList<>(children.size());
        ScannedNode<N> current = new ScannedNode<>(node, path, snapshot);
        scanned.put(node, current);
        for (Map.Entry<String, N> entry : children.entrySet()) {
            String rawName = entry.getKey();
            if (rawName == null || rawName.length() > MAX_PATH_BYTES) {
                throw new IllegalArgumentException("invalid model child name");
            }
            String encoded = PoseStateCodec.encodeSegment(rawName);
            N child = entry.getValue();
            if (child == null) {
                throw new IllegalArgumentException("null model child");
            }
            String childPath = "/".equals(path)
                    ? path + encoded : path + "/" + encoded;
            if (childPath.getBytes(StandardCharsets.UTF_8).length > MAX_PATH_BYTES) {
                throw new IllegalArgumentException("model path exceeds byte budget");
            }
            snapshot.add(new Child<>(child));
            scan(child, childPath, depth + 1, access, scanned);
        }
    }

    private static <N> void append(
            ScannedNode<N> node,
            int parentIndex,
            int siblingOrdinal,
            boolean parentEffectiveVisible,
            boolean parentZeroScale,
            NodeAccess<N> access,
            IdentityHashMap<N, ScannedNode<N>> scanned,
            List<PoseState.Node> target
    ) {
        RenderFlags flags = Objects.requireNonNull(
                access.flags(node.identity), "model render flags"
        );
        boolean effectiveVisible = parentEffectiveVisible && flags.visible();
        boolean traversed = effectiveVisible
                && (!flags.empty() || !node.children.isEmpty());
        boolean zeroScale = parentZeroScale;
        boolean entered = false;
        try {
            if (traversed) {
                zeroScale |= access.enter(node.identity);
                entered = true;
            }
            PoseState.RuntimeFrame frame = null;
            boolean skipDraw = flags.skipDraw();
            if (effectiveVisible && !flags.empty() && !skipDraw) {
                FrameDecision decision = Objects.requireNonNull(
                        access.frame(node.identity, zeroScale), "absolute model frame decision"
                );
                if (decision.suppressDraw()) {
                    access.suppressDraw(node.identity);
                    skipDraw = true;
                } else {
                    frame = decision.runtimeFrame();
                }
            }
            int index = target.size();
            target.add(new PoseState.Node(
                    node.path, parentIndex, siblingOrdinal, frame,
                    flags.visible(), skipDraw
            ));
            for (int ordinal = 0; ordinal < node.children.size(); ordinal++) {
                Child<N> child = node.children.get(ordinal);
                ScannedNode<N> scannedChild = scanned.get(child.identity);
                if (scannedChild == null) {
                    throw new IllegalArgumentException("captured model child vanished");
                }
                append(
                        scannedChild, index, ordinal, effectiveVisible, zeroScale,
                        access, scanned, target
                );
            }
        } finally {
            if (entered) {
                access.exit(node.identity);
            }
        }
    }

    record CapturedTree(String selectedRootPath, List<PoseState.Node> nodes) {
        CapturedTree {
            nodes = List.copyOf(nodes);
            new PoseState(selectedRootPath, 0, nodes);
        }

        PoseState withTint(int tintArgb) {
            return new PoseState(selectedRootPath, tintArgb, nodes);
        }
    }

    record RenderFlags(
            boolean visible,
            boolean skipDraw,
            boolean empty
    ) {
    }

    record FrameDecision(PoseState.RuntimeFrame runtimeFrame, boolean suppressDraw) {
        FrameDecision {
            if (suppressDraw == (runtimeFrame != null)) {
                throw new IllegalArgumentException("invalid absolute model frame decision");
            }
        }

        static FrameDecision captured(PoseState.RuntimeFrame runtimeFrame) {
            return new FrameDecision(
                    Objects.requireNonNull(runtimeFrame, "runtimeFrame"), false
            );
        }

        static FrameDecision suppress() {
            return new FrameDecision(null, true);
        }
    }

    interface NodeAccess<N> {
        Map<String, N> children(N node);

        RenderFlags flags(N node);

        /** Enters the node transform and returns whether its local scale has an exact zero axis. */
        boolean enter(N node);

        FrameDecision frame(N node, boolean zeroScaleInPath);

        void suppressDraw(N node);

        void exit(N node);
    }

    private record Child<N>(N identity) {
    }

    private static final class ScannedNode<N> {
        private final N identity;
        private final String path;
        private final List<Child<N>> children;

        private ScannedNode(N identity, String path, List<Child<N>> children) {
            this.identity = identity;
            this.path = path;
            this.children = children;
        }
    }
}
