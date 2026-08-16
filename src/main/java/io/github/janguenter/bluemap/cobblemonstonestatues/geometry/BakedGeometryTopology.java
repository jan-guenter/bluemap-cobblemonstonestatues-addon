/*
 * SPDX-License-Identifier: MPL-2.0
 *
 * Copyright (C) 2023 Cobblemon Contributors
 * Copyright (C) 2026 Jan Guenter
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Adapted from Cobblemon TexturedModel.kt at exact commit
 * 37264aff0f6c90c9f9a79bbdfc712bd11929a38c, path
 * common/src/main/kotlin/com/cobblemon/mod/common/client/render/models/
 * blockbench/TexturedModel.kt. Modifications replace the client LayerDefinition
 * output with a validated, order-neutral topology representation and bind it to
 * an externally captured render-order/runtime-state closure.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry;

import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseStateCodec;
import io.github.janguenter.bluemap.cobblemonstonestatues.pose.PoseState;
import io.github.janguenter.bluemap.cobblemonstonestatues.catalog.FormatTwoBudgets;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/** MPL-covered topology adaptation of Cobblemon 1.7.3 TexturedModel. */
public record BakedGeometryTopology(
        String sourceFormatVersion,
        String identifier,
        int textureWidth,
        int textureHeight,
        int sourceBoneCount,
        int sourceCubeCount,
        int sourceLocatorCount,
        List<Node> nodes
) {

    public static final String LOCATOR_PREFIX = "internal_locator__";
    public BakedGeometryTopology {
        Objects.requireNonNull(sourceFormatVersion, "sourceFormatVersion");
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(nodes, "nodes");
        if (nodes.isEmpty() || nodes.size() > FormatTwoBudgets.MAX_MODEL_TOPOLOGY_NODES) {
            throw new IllegalArgumentException("baked geometry node count outside budget");
        }
        nodes = List.copyOf(nodes);
        if (!"/".equals(nodes.getFirst().path())) {
            throw new IllegalArgumentException("baked geometry lacks wrapper root");
        }
    }

    public static BakedGeometryTopology bake(BedrockGeometry geometry) {
        MutableNode wrapper = new MutableNode("", identity());
        Map<String, MutableNode> parts = new HashMap<>();
        Map<String, BedrockGeometry.Bone> bones = new HashMap<>();

        for (BedrockGeometry.Bone bone : geometry.bones()) {
            MutableNode parent = bone.parent() == null ? wrapper : parts.get(bone.parent());
            BedrockGeometry.Bone parentBone = bone.parent() == null ? null : bones.get(bone.parent());
            if (parent == null || bone.parent() != null && parentBone == null) {
                throw new IllegalArgumentException("missing baked geometry parent");
            }
            Transform transform = parentBone == null
                    ? identity()
                    : relativeTransform(parentBone.pivot(), bone.pivot(), bone.rotationDegrees());
            MutableNode node = new MutableNode(bone.name(), transform);
            parent.add(node);
            if (parts.putIfAbsent(bone.name(), node) != null
                    || bones.putIfAbsent(bone.name(), bone) != null) {
                throw new IllegalArgumentException("duplicate baked geometry bone");
            }

            int rotatedCubeIndex = 0;
            for (BedrockGeometry.Cube cube : bone.cubes()) {
                BedrockGeometry.Vec3 pivot = cube.pivot() == null ? bone.pivot() : cube.pivot();
                Box box = box(cube, pivot);
                if (cube.rotationDegrees() == null) {
                    node.boxes.add(box);
                } else {
                    String name = "%" + bone.name() + "%" + rotatedCubeIndex++;
                    MutableNode rotated = new MutableNode(
                            name,
                            relativeTransform(bone.pivot(), pivot, cube.rotationDegrees())
                    );
                    rotated.boxes.add(box);
                    node.add(rotated);
                }
            }
        }

        for (BedrockGeometry.Bone bone : geometry.bones()) {
            MutableNode parent = parts.get(bone.name());
            for (BedrockGeometry.Locator locator : bone.locators()) {
                MutableNode child = new MutableNode(
                        LOCATOR_PREFIX + locator.name(),
                        relativeTransform(bone.pivot(), locator.offset(), locator.rotationDegrees())
                );
                parent.add(child);
            }
        }
        wrapper.add(new MutableNode(LOCATOR_PREFIX + "root", identity()));

        List<Node> flattened = new ArrayList<>();
        flatten(wrapper, flattened);
        return new BakedGeometryTopology(
                geometry.formatVersion(), geometry.identifier(),
                geometry.textureWidth(), geometry.textureHeight(),
                geometry.bones().size(), geometry.cubeCount(), geometry.locatorCount(), flattened
        );
    }

    /**
     * Binds this order-neutral topology to the exact client ModelPart traversal captured in a pose.
     */
    public BoundGeometry bind(PoseState state) {
        boolean wrapperSelected = "/".equals(state.selectedRootPath());
        Map<String, Node> byPath = new HashMap<>();
        for (Node node : nodes) {
            if (byPath.putIfAbsent(node.path(), node) != null) {
                throw new IllegalArgumentException("duplicate baked topology path");
            }
        }
        Node selectedRoot = byPath.get(state.selectedRootPath());
        if (selectedRoot == null) {
            throw new IllegalArgumentException("selected root is absent from GEO topology");
        }
        Set<String> expectedSubtree = new HashSet<>();
        for (String path : byPath.keySet()) {
            if (wrapperSelected || path.equals(state.selectedRootPath())
                    || path.startsWith(state.selectedRootPath() + "/")) {
                expectedSubtree.add(path);
            }
        }
        if (state.nodes().size() != expectedSubtree.size()) {
            throw new IllegalArgumentException("pose-state selected-subtree count mismatch");
        }
        List<BoundNode> ordered = new ArrayList<>(state.nodes().size());
        Set<String> visited = new HashSet<>();
        Set<String> matchedTopology = new HashSet<>();
        boolean[] effectiveVisibility = new boolean[state.nodes().size()];
        for (int index = 0; index < state.nodes().size(); index++) {
            PoseState.Node stateNode = state.nodes().get(index);
            Node geometryNode = byPath.get(stateNode.path());
            if (geometryNode == null || !visited.add(stateNode.path())) {
                throw new IllegalArgumentException("pose-state topology path mismatch");
            }
            if (!matchedTopology.add(geometryNode.path())) {
                throw new IllegalArgumentException("duplicate pose-state topology node");
            }
            String expectedParent = stateNode.parentIndex() < 0
                    ? null : state.nodes().get(stateNode.parentIndex()).path();
            if (index != 0
                    && !Objects.equals(expectedParent, geometryNode.parentPath())) {
                throw new IllegalArgumentException("pose-state topology parent mismatch");
            }
            boolean parentVisible = stateNode.parentIndex() < 0
                    || effectiveVisibility[stateNode.parentIndex()];
            effectiveVisibility[index] = parentVisible && stateNode.visible();
            boolean frameRequired = effectiveVisibility[index]
                    && !stateNode.skipDraw() && !geometryNode.boxes().isEmpty();
            if ((stateNode.runtimeFrame() != null) != frameRequired) {
                throw new IllegalArgumentException("pose-state runtime-frame closure mismatch");
            }
            ordered.add(new BoundNode(geometryNode, stateNode));
        }
        if (!matchedTopology.equals(expectedSubtree)) {
            throw new IllegalArgumentException("pose-state topology closure mismatch");
        }
        return new BoundGeometry(identifier, textureWidth, textureHeight, ordered);
    }

    private static Box box(BedrockGeometry.Cube cube, BedrockGeometry.Vec3 pivot) {
        BedrockGeometry.Vec3 origin = cube.origin();
        BedrockGeometry.Vec3 size = cube.size();
        return new Box(
                new BedrockGeometry.Vec3(
                        origin.x() - pivot.x(),
                        -(origin.y() - pivot.y() + size.y()),
                        origin.z() - pivot.z()
                ),
                size,
                cube.u(), cube.v(), cube.inflate(), cube.mirror()
        );
    }

    private static Transform relativeTransform(
            BedrockGeometry.Vec3 parentPivot,
            BedrockGeometry.Vec3 childPivot,
            BedrockGeometry.Vec3 rotationDegrees
    ) {
        BedrockGeometry.Vec3 rotation = rotationDegrees == null
                ? new BedrockGeometry.Vec3(0F, 0F, 0F)
                : new BedrockGeometry.Vec3(
                        radians(rotationDegrees.x()),
                        radians(rotationDegrees.y()),
                        radians(rotationDegrees.z())
                );
        return new Transform(
                new BedrockGeometry.Vec3(
                        -(parentPivot.x() - childPivot.x()),
                        parentPivot.y() - childPivot.y(),
                        -(parentPivot.z() - childPivot.z())
                ),
                rotation,
                new BedrockGeometry.Vec3(1F, 1F, 1F)
        );
    }

    private static Transform identity() {
        return new Transform(
                new BedrockGeometry.Vec3(0F, 0F, 0F),
                new BedrockGeometry.Vec3(0F, 0F, 0F),
                new BedrockGeometry.Vec3(1F, 1F, 1F)
        );
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians((double) degrees);
    }

    private static void flatten(MutableNode root, List<Node> target) {
        ArrayDeque<PendingNode> pending = new ArrayDeque<>();
        pending.addLast(new PendingNode(root, null, "/", 0));
        while (!pending.isEmpty()) {
            PendingNode current = pending.removeLast();
            if (current.depth() > FormatTwoBudgets.MAX_MODEL_DEPTH) {
                throw new IllegalArgumentException("baked topology depth outside budget");
            }
            if (target.size() >= FormatTwoBudgets.MAX_MODEL_TOPOLOGY_NODES) {
                throw new IllegalArgumentException("baked topology node count outside budget");
            }
            MutableNode source = current.source();
            target.add(new Node(
                    source.name, current.path(), current.parentPath(),
                    source.transform, source.boxes
            ));
            for (int index = source.children.size() - 1; index >= 0; index--) {
                MutableNode child = source.children.get(index);
                String childPath = "/".equals(current.path())
                        ? current.path() + PoseStateCodec.encodeSegment(child.name)
                        : current.path() + "/" + PoseStateCodec.encodeSegment(child.name);
                pending.addLast(new PendingNode(
                        child, current.path(), childPath, current.depth() + 1
                ));
            }
        }
    }

    private record PendingNode(
            MutableNode source,
            String parentPath,
            String path,
            int depth
    ) {
    }

    public record Node(
            String name,
            String path,
            String parentPath,
            Transform defaultTransform,
            List<Box> boxes
    ) {

        public Node {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(defaultTransform, "defaultTransform");
            boxes = List.copyOf(boxes);
        }
    }

    public record Transform(
            BedrockGeometry.Vec3 position,
            BedrockGeometry.Vec3 rotationRadians,
            BedrockGeometry.Vec3 scale
    ) {

        public Transform {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(rotationRadians, "rotationRadians");
            Objects.requireNonNull(scale, "scale");
        }
    }

    public record Box(
            BedrockGeometry.Vec3 origin,
            BedrockGeometry.Vec3 size,
            int u,
            int v,
            float inflate,
            boolean mirror
    ) {

        public Box {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(size, "size");
        }
    }

    public record BoundGeometry(
            String identifier,
            int textureWidth,
            int textureHeight,
            List<BoundNode> nodes
    ) {

        public BoundGeometry {
            Objects.requireNonNull(identifier, "identifier");
            nodes = List.copyOf(nodes);
        }
    }

    public record BoundNode(Node geometry, PoseState.Node pose) {

        public BoundNode {
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(pose, "pose");
            if (!geometry.path().equals(pose.path())) {
                throw new IllegalArgumentException("bound topology path mismatch");
            }
        }
    }

    private static final class MutableNode {

        private final String name;
        private final Transform transform;
        private final List<Box> boxes = new ArrayList<>();
        private final List<MutableNode> children = new ArrayList<>();
        private final Map<String, MutableNode> childrenByName = new HashMap<>();

        private MutableNode(String name, Transform transform) {
            this.name = name;
            this.transform = transform;
        }

        private void add(MutableNode child) {
            if (childrenByName.putIfAbsent(child.name, child) != null) {
                throw new IllegalArgumentException("duplicate baked geometry child name");
            }
            children.add(child);
        }
    }
}
