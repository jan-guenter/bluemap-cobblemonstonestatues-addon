/* SPDX-License-Identifier: MIT */
package io.github.janguenter.bluemap.cobblemonstonestatues.adapter.bluemap523;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModel;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel.Quad;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel.Vec3;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueModel.Vertex;
import io.github.janguenter.bluemap.cobblemonstonestatues.model.StatueSelection;

/** Emits client-captured outward entityCutout quads once. */
final class StatueMeshEmitter implements StoneStatuesRenderer.MeshEmitter {

    private static final int[][] TRIANGLE_ORDER = {{0, 1, 2}, {0, 2, 3}};
    private final ResourcePack resourcePack;
    private final TextureGallery textureGallery;
    private final RenderSettings renderSettings;
    private float mapColorOpacity;

    StatueMeshEmitter(
            ResourcePack resourcePack,
            TextureGallery textureGallery,
            RenderSettings renderSettings
    ) {
        this.resourcePack = resourcePack;
        this.textureGallery = textureGallery;
        this.renderSettings = renderSettings;
    }

    @Override
    public void beginVariantColor() {
        mapColorOpacity = 0F;
    }

    @Override
    public void finishVariantColor(Color mapColor) {
        if (mapColor.a > 0F) {
            mapColor.flatten().straight();
            mapColor.a = mapColorOpacity;
        }
    }

    @Override
    public boolean emit(
            StatueModel model,
            StatueSelection selection,
            BlockNeighborhood block,
            TileModelView target,
            Color mapColor
    ) {
        Texture texture = resourcePack.getTextures().get(model.texture());
        if (texture == null) {
            return false;
        }
        int material = textureGallery.get(model.texture());
        int renderableTransforms = 0;
        List<RenderedQuad> rendered = new ArrayList<>();
        for (Quad source : model.quads()) {
            Optional<Quad> transformed = transformVisible(source, selection);
            if (transformed.isEmpty()) {
                continue;
            }
            Quad quad = transformed.orElseThrow();
            renderableTransforms++;
            Vec3 normal = quad.normal();
            if (renderSettings.isRenderTopOnly() && normal.y() <= 0D) {
                continue;
            }
            FaceLighting.Sample light = FaceLighting.sample(block, nearestDirection(normal));
            if (hiddenByCave(block.isRemoveIfCave(),
                    renderSettings.isCaveDetectionUsesBlockLight(), light)) {
                continue;
            }
            rendered.add(new RenderedQuad(quad, light));
            if (normal.y() > 0D) {
                Color average = new Color().set(texture.getColorPremultiplied());
                float lightFactor = Math.max(light.sunlight(), light.blocklight()) / 15F;
                lightFactor = (1F - renderSettings.getAmbientLight()) * lightFactor
                        + renderSettings.getAmbientLight();
                average.r *= lightFactor;
                average.g *= lightFactor;
                average.b *= lightFactor;
                mapColorOpacity = Math.max(mapColorOpacity, average.a);
                mapColor.add(average);
            }
        }
        if (!rendered.isEmpty()) {
            int start = reserveTriangles(target, rendered.size());
            TileModel mesh = target.getTileModel();
            for (int index = 0; index < rendered.size(); index++) {
                RenderedQuad entry = rendered.get(index);
                writeQuad(
                        entry.quad(), mesh,
                        start + emittedTriangleCount(index), material, entry.light()
                );
            }
        }
        return renderableTransforms > 0;
    }

    static Quad transform(Quad quad, StatueSelection selection) {
        return transformVisible(quad, selection).orElseThrow(() ->
                new IllegalArgumentException("statue quad collapsed under block transform"));
    }

    static Optional<Quad> transformVisible(Quad quad, StatueSelection selection) {
        Vertex first = transform(quad.first(), selection);
        Vertex second = transform(quad.second(), selection);
        Vertex third = transform(quad.third(), selection);
        Vertex fourth = transform(quad.fourth(), selection);
        Vec3 firstCross = second.position().subtract(first.position())
                .cross(third.position().subtract(first.position()));
        Vec3 secondCross = fourth.position().subtract(third.position())
                .cross(first.position().subtract(third.position()));
        if (isExactZero(firstCross) && isExactZero(secondCross)) {
            return Optional.empty();
        }
        return Optional.of(new Quad(
                first, second, third, fourth,
                transformNormal(quad.storedNormal(), selection)
        ));
    }

    private static boolean isExactZero(Vec3 value) {
        return value.x() == 0D && value.y() == 0D && value.z() == 0D;
    }

    private static Vec3 transformNormal(Vec3 normal, StatueSelection selection) {
        Vec3 reflected = new Vec3(normal.x(), -normal.y(), -normal.z());
        return reflected.rotateWorldY(Math.toRadians(selection.rotation45() * 45D));
    }

    private static Vertex transform(Vertex source, StatueSelection selection) {
        double scale = selection.scale().factor();
        Vec3 point = source.position()
                .multiply(new Vec3(scale, -scale, -scale))
                .rotateWorldY(Math.toRadians(selection.rotation45() * 45D))
                .add(new Vec3(0.5D, 0D, 0.5D));
        return new Vertex(point, source.u(), source.v());
    }

    private static void writeQuad(
            Quad quad,
            TileModel mesh,
            int start,
            int material,
            FaceLighting.Sample light
    ) {
        Vertex[] vertices = {quad.first(), quad.second(), quad.third(), quad.fourth()};
        for (int triangle = 0; triangle < TRIANGLE_ORDER.length; triangle++) {
            int[] order = TRIANGLE_ORDER[triangle];
            Vertex a = vertices[order[0]];
            Vertex b = vertices[order[1]];
            Vertex c = vertices[order[2]];
            mesh.setPositions(
                    start + triangle,
                    (float) a.position().x(), (float) a.position().y(), (float) a.position().z(),
                    (float) b.position().x(), (float) b.position().y(), (float) b.position().z(),
                    (float) c.position().x(), (float) c.position().y(), (float) c.position().z()
            );
            mesh.setUvs(start + triangle, a.u(), a.v(), b.u(), b.v(), c.u(), c.v());
            mesh.setMaterialIndex(start + triangle, material);
            mesh.setColor(start + triangle, 1F, 1F, 1F);
            mesh.setAOs(start + triangle, 1F, 1F, 1F);
            mesh.setSunlight(start + triangle, light.sunlight());
            mesh.setBlocklight(start + triangle, light.blocklight());
        }
    }

    static int reserveTriangles(TileModelView target, int sourceQuads) {
        int viewStart = target.getStart();
        int start = target.getTileModel().add(emittedTriangleCount(sourceQuads));
        target.initialize(viewStart);
        return start;
    }

    static int emittedTriangleCount(int sourceQuads) {
        if (sourceQuads < 0) {
            throw new IllegalArgumentException("negative source quad count");
        }
        return Math.multiplyExact(sourceQuads, TRIANGLE_ORDER.length);
    }

    static int[] triangleOrder(int triangle) {
        if (triangle < 0 || triangle >= TRIANGLE_ORDER.length) {
            throw new IllegalArgumentException("invalid triangle index");
        }
        return TRIANGLE_ORDER[triangle].clone();
    }

    static boolean hiddenByCave(
            boolean removeIfCave, boolean usesBlockLight, FaceLighting.Sample light
    ) {
        return removeIfCave && light.sunlight() == 0
                && (!usesBlockLight || light.blocklight() == 0);
    }

    static Direction nearestDirection(Vec3 normal) {
        double x = Math.abs(normal.x());
        double y = Math.abs(normal.y());
        double z = Math.abs(normal.z());
        if (y >= x && y >= z) {
            return normal.y() >= 0D ? Direction.UP : Direction.DOWN;
        }
        if (x >= z) {
            return normal.x() >= 0D ? Direction.EAST : Direction.WEST;
        }
        return normal.z() >= 0D ? Direction.SOUTH : Direction.NORTH;
    }

    private record RenderedQuad(Quad quad, FaceLighting.Sample light) {
    }
}
