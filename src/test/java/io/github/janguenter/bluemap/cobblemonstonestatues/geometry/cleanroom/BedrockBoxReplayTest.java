// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Jan Guenter
/*
 * Clean-room provenance: SPEC.md; Microsoft Bedrock geometry 1.12 schema/visual docs;
 * sanitized fixture SHA-256 b287b9ff5b6bdd0aa9d7bf04865a1d9bb28f772e59b10333c224ea552fbc848f.
 */
package io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom;

import static io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.BoxToMatrixSpace.IDENTITY;
import static io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.EndpointDeformation.RUNTIME_FIXED_ENDPOINTS;

import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.BoxToMatrixSpace;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.CanonicalQuad;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Cube;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Face;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.FinalNodeFrame;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.RuntimeBoxUv;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Quad;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Vec2;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Vec3;
import io.github.janguenter.bluemap.cobblemonstonestatues.geometry.cleanroom.BedrockBoxReplay.Vertex;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Matrix4f;

/** Plain-Java tests: run with {@code java -ea}. */
public final class BedrockBoxReplayTest {
    private static final RuntimeBoxUv RUNTIME_UV = new RuntimeBoxUv();

    private BedrockBoxReplayTest() {
    }

    public static void main(String[] args) {
        positiveGeometryUsesChosenWindingAndUvAssociation();
        finalPositionMatrixRunsOnceAndNormalsAreCopiedRaw();
        mirrorFlipsFaceLocalUInObservedRuntimePolicy();
        declaredZeroSpanFiltersOnlyZeroAreaFaces();
        transformCollapseIsFilteredAfterTransform();
        tinyNonzeroFacesSurviveDoubleDegeneracyCheck();
        secondTriangleUses230EvaluationAtFloatingExtremes();
        signedSizesAreNeverSortedOrMadeAbsolute();
        negativeDeformationRemainsSignedAndConventionIsExplicit();
        negativeUvAndSignedUvSpansRemainSigned();
        fractionalSizesRemainFractionalInObservedRuntimeUv();
        unitBoundaryIsExplicitAndHasNoCompilerDefault();
        handednessReversalDoesNotRepairVertexOrder();
        normalGateRunsBeforeDegenerateDrop();
        retainedNormalsRemainRawWhenQuantizedGeometryIsIllConditioned();
        rawF32EndpointsAndSignedZeroArePreserved();
        exactProfileRejectsUncalibratedAndInvalidInputs();
        canonicalizationAllowsCyclesButNeverReversal();
        canonicalizationUsesRawF32BitsAndMultisetMultiplicity();
        inputValidationRejectsOnlyUnsupportedStructuralValues();
        System.out.println("BedrockBoxReplayTest: all tests passed");
    }

    private static void positiveGeometryUsesChosenWindingAndUvAssociation() {
        Cube cube = cube(
                new Vec3(1.0f, 2.0f, 3.0f),
                new Vec3(2.0f, 3.0f, 5.0f),
                0.0f,
                false,
                new Vec2(7.0f, 11.0f),
                64,
                32);

        List<Quad> quads = compile(cube, frame(IDENTITY, position -> position, cardinalNormals()));
        assertEquals(6, quads.size(), "positive cube face count");

        Quad north = face(quads, Face.NORTH);
        assertVecRaw(new Vec3(3.0f, 5.0f, 3.0f), north.vertices().get(0).position(), "north TL");
        assertVecRaw(new Vec3(3.0f, 2.0f, 3.0f), north.vertices().get(1).position(), "north BL");
        assertVecRaw(new Vec3(1.0f, 2.0f, 3.0f), north.vertices().get(2).position(), "north BR");
        assertVecRaw(new Vec3(1.0f, 5.0f, 3.0f), north.vertices().get(3).position(), "north TR");

        assertVec2Raw(new Vec2(14.0f / 64.0f, 19.0f / 32.0f),
                north.vertices().get(0).uv(), "north TL uv");
        assertVec2Raw(new Vec2(14.0f / 64.0f, 16.0f / 32.0f),
                north.vertices().get(1).uv(), "north BL uv");
        assertVec2Raw(new Vec2(12.0f / 64.0f, 16.0f / 32.0f),
                north.vertices().get(2).uv(), "north BR uv");
        assertVec2Raw(new Vec2(12.0f / 64.0f, 19.0f / 32.0f),
                north.vertices().get(3).uv(), "north TR uv");

        for (Quad quad : quads) {
            Vec3 cross = firstTriangleCross(quad);
            Vec3 cardinal = cardinalNormals().get(quad.sourceFace());
            float dot = cross.x() * cardinal.x()
                    + cross.y() * cardinal.y()
                    + cross.z() * cardinal.z();
            assertTrue(dot > 0.0f, "chosen winding should face outward for positive spans: "
                    + quad.sourceFace());
        }
    }

    private static void finalPositionMatrixRunsOnceAndNormalsAreCopiedRaw() {
        Cube cube = cube(
                new Vec3(1.0f, 2.0f, 3.0f),
                new Vec3(2.0f, 3.0f, 5.0f),
                0.5f,
                false,
                new Vec2(7.0f, 11.0f),
                64,
                32);
        Map<Face, Vec3> distinctiveNormals = distinctiveNormals();
        AtomicInteger calls = new AtomicInteger();

        FinalNodeFrame frame = frame(IDENTITY, position -> {
            calls.incrementAndGet();
            float x = 10.0f + 2.0f * position.x();
            float y = -4.0f + 3.0f * position.y();
            float z = 5.0f + 4.0f * position.z();
            return new Vec3(x, y, z);
        }, distinctiveNormals);

        List<Quad> quads = compile(cube, frame);
        assertEquals(24, calls.get(), "one transform call for each candidate vertex");
        Quad north = face(quads, Face.NORTH);
        // Inflated local TL is (3.5, 5.5, 2.5), transformed once.
        assertVecRaw(new Vec3(17.0f, 12.5f, 15.0f), north.vertices().get(0).position(),
                "transformed inflated north TL");
        assertVecRaw(distinctiveNormals.get(Face.NORTH), north.normal(),
                "captured normal must pass through unchanged");
        // Deformation does not change the black-box-backed UV rectangle.
        assertVec2Raw(new Vec2(14.0f / 64.0f, 19.0f / 32.0f),
                north.vertices().get(0).uv(), "inflation-independent UV");
    }

    private static void mirrorFlipsFaceLocalUInObservedRuntimePolicy() {
        Cube plain = cube(
                new Vec3(1.0f, 2.0f, 3.0f),
                new Vec3(2.0f, 3.0f, 5.0f),
                0.0f,
                false,
                new Vec2(7.0f, 11.0f),
                64,
                32);
        Cube mirrored = new Cube(
                plain.origin(), plain.size(), plain.deformation(), true, plain.boxUv(),
                plain.textureWidth(), plain.textureHeight());

        List<Quad> normalQuads = compile(plain, frame(IDENTITY, p -> p, distinctiveNormals()));
        List<Quad> mirrorQuads = compile(mirrored, frame(IDENTITY, p -> p, distinctiveNormals()));
        Quad normalNorth = face(normalQuads, Face.NORTH);
        Quad mirrorNorth = face(mirrorQuads, Face.NORTH);

        for (int index = 0; index < 4; index++) {
            assertVecRaw(normalNorth.vertices().get(index).position(),
                    mirrorNorth.vertices().get(index).position(), "mirror position " + index);
        }
        assertRaw(12.0f / 64.0f, mirrorNorth.vertices().get(0).uv().u(), "mirrored first u");
        assertRaw(14.0f / 64.0f, mirrorNorth.vertices().get(3).uv().u(), "mirrored fourth u");
        assertVecRaw(normalNorth.normal(), mirrorNorth.normal(), "mirror normal passthrough");
    }

    private static void declaredZeroSpanFiltersOnlyZeroAreaFaces() {
        Cube cube = cube(
                new Vec3(1.0f, 2.0f, 3.0f),
                new Vec3(0.0f, 3.0f, 5.0f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                64,
                64);
        List<Quad> quads = compile(cube, frame(IDENTITY, p -> p, cardinalNormals()));
        assertFaceSet(quads, Face.EAST, Face.WEST);
        assertEquals(2, quads.size(), "coincident nonzero faces retain multiplicity");
    }

    private static void transformCollapseIsFilteredAfterTransform() {
        Cube cube = cube(
                new Vec3(1.0f, 2.0f, 3.0f),
                new Vec3(2.0f, 3.0f, 5.0f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                64,
                64);
        AtomicInteger calls = new AtomicInteger();
        FinalNodeFrame frame = frame(IDENTITY, p -> {
            calls.incrementAndGet();
            return new Vec3(0.0f, p.y(), p.z());
        }, cardinalNormals());

        List<Quad> quads = compile(cube, frame);
        assertEquals(24, calls.get(), "all candidates transform before filtering");
        assertFaceSet(quads, Face.EAST, Face.WEST);
    }

    private static void tinyNonzeroFacesSurviveDoubleDegeneracyCheck() {
        Cube cube = cube(
                new Vec3(0.0f, 0.0f, 0.0f),
                new Vec3(1.0e-30f, 1.0e-30f, 1.0e-30f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                64,
                64);

        List<Quad> quads = compile(cube, frame(IDENTITY, p -> p, cardinalNormals()));
        assertEquals(6, quads.size(),
                "1e-30f spans have nonzero double area even though f32 cross underflows");
        // Demonstrate the regression's discriminator: the old f32 helper does underflow.
        assertTrue(firstTriangleCross(face(quads, Face.SOUTH)).isZero(),
                "control: f32 cross underflows to zero for tiny face");
    }

    private static void secondTriangleUses230EvaluationAtFloatingExtremes() {
        float huge = Float.MAX_VALUE;
        Vec3 p0 = new Vec3(huge, huge, 0.0f);
        Vec3 p1 = p0;
        Vec3 p2 = new Vec3(1.0f, 0.0f, 0.0f);
        Vec3 p3 = new Vec3(0.0f, 1.0f, 0.0f);
        Quad candidate = new Quad(Face.NORTH, List.of(
                new Vertex(p0, new Vec2(0.0f, 0.0f)),
                new Vertex(p1, new Vec2(0.0f, 0.0f)),
                new Vertex(p2, new Vec2(0.0f, 0.0f)),
                new Vertex(p3, new Vec2(0.0f, 0.0f))),
                new Vec3(0.0f, 0.0f, -1.0f));

        double[] required230 = doubleTriangleCross(p2, p3, p0);
        double[] rewritten023 = doubleTriangleCross(p0, p2, p3);
        assertFalse(isExactZero(required230),
                "required [2,3,0] evaluation retains the extreme second triangle");
        assertTrue(isExactZero(rewritten023),
                "algebraic [0,2,3] rewrite rounds the extreme second triangle to zero");
        assertEquals(1, BedrockBoxReplay.validateAndFilterCandidates(List.of(candidate)).size(),
                "required [2,3,0] split must retain the candidate");
    }

    private static void signedSizesAreNeverSortedOrMadeAbsolute() {
        Cube cube = cube(
                new Vec3(10.0f, 2.0f, 3.0f),
                new Vec3(-2.0f, 3.0f, 5.0f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                64,
                64);
        List<Quad> quads = compile(cube, frame(IDENTITY, p -> p, cardinalNormals()));
        assertEquals(6, quads.size(), "nonzero signed cube face count");

        Quad north = face(quads, Face.NORTH);
        // xB is origin+size = 8 and remains the first algebraic slot; it is not sorted to 10.
        assertRaw(8.0f, north.vertices().get(0).position().x(), "signed xB preserved");
        assertRaw(10.0f, north.vertices().get(2).position().x(), "signed xA preserved");
        assertTrue(firstTriangleCross(north).z() > 0.0f,
                "negative X span reverses chosen NORTH geometric winding without repair");
        assertVecRaw(new Vec3(0.0f, 0.0f, -1.0f), north.normal(),
                "nominal NORTH final normal is not repaired");

        // The observed north UV width is signed too: right = depth + width = 3.
        assertRaw(3.0f / 64.0f, north.vertices().get(0).uv().u(), "signed north first UV");
        assertRaw(5.0f / 64.0f, north.vertices().get(3).uv().u(), "signed north fourth UV");
    }

    private static void negativeDeformationRemainsSignedAndConventionIsExplicit() {
        Cube positive = new Cube(
                new Vec3(4.125f, -0.75f, 2.25f),
                new Vec3(-3.0f, 4.0f, 5.0f),
                new Vec3(0.5f, 0.0f, 0.0f),
                false,
                new Vec2(4.0f, 12.0f),
                61,
                37);
        Cube negative = new Cube(
                positive.origin(),
                positive.size(),
                new Vec3(-0.5f, 0.0f, 0.0f),
                false,
                positive.boxUv(),
                positive.textureWidth(),
                positive.textureHeight());
        FinalNodeFrame frame = frame(IDENTITY, p -> p, cardinalNormals());

        Quad positiveNorth = face(BedrockBoxReplay.compile(positive, frame,
                RUNTIME_FIXED_ENDPOINTS, RUNTIME_UV), Face.NORTH);
        Quad negativeNorth = face(BedrockBoxReplay.compile(negative, frame,
                RUNTIME_FIXED_ENDPOINTS, RUNTIME_UV), Face.NORTH);

        // Positive deformation with signed size: xA=3.625, xB=1.625.
        assertRaw(1.625f, positiveNorth.vertices().get(0).position().x(),
                "positive deformation signed xB");
        assertRaw(3.625f, positiveNorth.vertices().get(2).position().x(),
                "positive deformation signed xA");
        // Negative deformation keeps endpoint roles fixed: xA=4.625, xB=.625.
        assertRaw(0.625f, negativeNorth.vertices().get(0).position().x(),
                "negative deformation signed xB");
        assertRaw(4.625f, negativeNorth.vertices().get(2).position().x(),
                "negative deformation signed xA");
    }

    private static void negativeUvAndSignedUvSpansRemainSigned() {
        Cube cube = cube(
                new Vec3(0.0f, 0.0f, 0.0f),
                new Vec3(-2.0f, 3.0f, -5.0f),
                0.0f,
                false,
                new Vec2(-7.0f, -11.0f),
                64,
                32);
        Quad west = face(compile(cube, frame(IDENTITY, p -> p, cardinalNormals())), Face.WEST);
        // WEST: left=U=-7, top=V+D=-16, width=D=-5, height=H=3.
        assertVec2Raw(new Vec2(-12.0f / 64.0f, -13.0f / 32.0f),
                west.vertices().get(0).uv(), "negative west TL");
        assertVec2Raw(new Vec2(-7.0f / 64.0f, -16.0f / 32.0f),
                west.vertices().get(2).uv(), "negative west BR");
    }

    private static void fractionalSizesRemainFractionalInObservedRuntimeUv() {
        Cube cube = cube(
                new Vec3(0.0f, 0.0f, 0.0f),
                new Vec3(2.5f, 3.25f, 4.75f),
                0.0f,
                false,
                new Vec2(-11.0f, -7.0f),
                53,
                47);
        FinalNodeFrame frame = frame(IDENTITY, p -> p, cardinalNormals());
        Quad north = face(BedrockBoxReplay.compile(cube, frame,
                RUNTIME_FIXED_ENDPOINTS, RUNTIME_UV), Face.NORTH);
        // NORTH left=-6.25, right=-3.75, top=-2.25, bottom=1.0; no floor.
        assertVec2Raw(new Vec2(-3.75f / 53.0f, 1.0f / 47.0f),
                north.vertices().get(0).uv(), "fractional north first");
        assertVec2Raw(new Vec2(-6.25f / 53.0f, -2.25f / 47.0f),
                north.vertices().get(2).uv(), "fractional north third");
    }

    private static void unitBoundaryIsExplicitAndHasNoCompilerDefault() {
        Cube cube = cube(
                new Vec3(16.0f, 16.0f, 16.0f),
                new Vec3(16.0f, 16.0f, 16.0f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                64,
                64);
        Quad identityNorth = face(compile(cube,
                frame(IDENTITY, p -> p, cardinalNormals())), Face.NORTH);
        Quad scaledNorth = face(compile(cube,
                frame(BoxToMatrixSpace.RUNTIME_ONE_SIXTEENTH, p -> p, cardinalNormals())),
                Face.NORTH);
        assertRaw(32.0f, identityNorth.vertices().get(0).position().x(), "identity units");
        assertRaw(2.0f, scaledNorth.vertices().get(0).position().x(), "explicit 1/16 units");
    }

    private static void handednessReversalDoesNotRepairVertexOrder() {
        Cube cube = cube(
                new Vec3(1.0f, 2.0f, 3.0f),
                new Vec3(2.0f, 3.0f, 5.0f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                64,
                64);
        List<Quad> quads = compile(cube, frame(IDENTITY,
                p -> new Vec3(-p.x(), p.y(), p.z()), cardinalNormals()));
        Quad south = face(quads, Face.SOUTH);
        assertTrue(firstTriangleCross(south).z() < 0.0f,
                "negative-X matrix reverses SOUTH winding without repair");
        assertVecRaw(new Vec3(0.0f, 0.0f, 1.0f), south.normal(),
                "final SOUTH normal remains captured value");
    }

    private static void normalGateRunsBeforeDegenerateDrop() {
        Cube point = cube(
                new Vec3(0.0f, 0.0f, 0.0f),
                new Vec3(0.0f, 0.0f, 0.0f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                64,
                64);
        EnumMap<Face, Vec3> normals = cardinalNormals();
        normals.put(Face.NORTH, new Vec3(2.0f, 0.0f, 0.0f));
        assertThrows(IllegalArgumentException.class,
                () -> compile(point, frame(IDENTITY, p -> p, normals)),
                "bad selected normal must fail before exactly-degenerate face is dropped");

        EnumMap<Face, Vec3> boundaryNormals = cardinalNormals();
        boundaryNormals.put(Face.NORTH, new Vec3(0.0f, 0.0f, -0.5f));
        boundaryNormals.put(Face.SOUTH, new Vec3(0.0f, 0.0f, 1.5f));
        assertEquals(0, compile(point, frame(IDENTITY, p -> p, boundaryNormals)).size(),
                "inclusive normal-length boundaries pass before all faces drop");
    }

    private static void retainedNormalsRemainRawWhenQuantizedGeometryIsIllConditioned() {
        Cube cube = cube(
                new Vec3(0.0f, 0.0f, 0.0f),
                new Vec3(2.0f, 3.0f, 5.0f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                64,
                64);

        EnumMap<Face, Vec3> atThreshold = cardinalNormals();
        atThreshold.put(Face.SOUTH, unitVectorWithZ(0.9990001f));
        assertEquals(6, compile(cube, frame(IDENTITY, p -> p, atThreshold)).size(),
                "stored runtime normal is retained");

        EnumMap<Face, Vec3> antiparallel = cardinalNormals();
        antiparallel.put(Face.SOUTH, new Vec3(0.0f, 0.0f, -1.0f));
        assertEquals(6, compile(cube, frame(IDENTITY, p -> p, antiparallel)).size(),
                "opposite stored runtime normal remains raw for digest verification");

        EnumMap<Face, Vec3> belowThreshold = cardinalNormals();
        belowThreshold.put(Face.SOUTH, unitVectorWithZ(0.9989f));
        assertEquals(6, compile(cube, frame(IDENTITY, p -> p, belowThreshold)).size(),
                "geometry-derived cosine does not replace the stored runtime normal");

        Vec3 malamarNormal = new Vec3(-0.69943154F, 0.5726601F, -0.4276163F);
        Quad malamarLightPlane = new Quad(Face.NORTH, List.of(
                vertex(rawPoint(0x401654f7, 0xc0452292, 0xbe35cc2e)),
                vertex(rawPoint(0x40165159, 0xc0452602, 0xbe35b730)),
                vertex(rawPoint(0x4016515b, 0xc0452608, 0xbe35b7c2)),
                vertex(rawPoint(0x401654f9, 0xc0452298, 0xbe35ccc0))
        ), malamarNormal);
        List<Quad> retained = BedrockBoxReplay.validateAndFilterCandidates(
                List.of(malamarLightPlane)
        );
        assertEquals(1, retained.size(), "quantization-limited runtime plane remains retained");
        assertVecRaw(malamarNormal, retained.getFirst().normal(),
                "Mega Malamar stored normal remains raw");
    }

    private static void rawF32EndpointsAndSignedZeroArePreserved() {
        Cube rounded = cube(
                new Vec3(16_777_216.0f, 0.0f, 0.0f),
                new Vec3(1.0f, 2.0f, 3.0f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                64,
                64);
        List<Quad> roundedQuads = compile(rounded, frame(IDENTITY, p -> p, cardinalNormals()));
        assertFaceSet(roundedQuads, Face.EAST, Face.WEST);
        Quad east = face(roundedQuads, Face.EAST);
        assertRaw(16_777_216.0f, east.vertices().get(0).position().x(),
                "origin+size is rounded as f32 before endpoint policy");

        Cube signedZero = cube(
                new Vec3(-0.0f, 1.0f, 1.0f),
                new Vec3(1.0f, 2.0f, 3.0f),
                0.0f,
                false,
                new Vec2(-0.0f, 0.0f),
                64,
                64);
        Quad south = face(compile(signedZero,
                frame(IDENTITY, p -> p, cardinalNormals())), Face.SOUTH);
        // SOUTH TL uses algebraic xA. Java f32 -0 - +0 retains the negative sign.
        assertRaw(-0.0f, south.vertices().get(0).position().x(), "signed-zero xA");
    }

    private static void exactProfileRejectsUncalibratedAndInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> cube(
                new Vec3(0.0f, 0.0f, 0.0f),
                new Vec3(1.0f, 1.0f, 1.0f),
                0.0f,
                false,
                new Vec2(0.5f, 0.0f),
                64,
                64), "fractional array box-UV origin is outside the calibrated profile");

        Matrix4f nonAffine = new Matrix4f().m03(0.25f);
        assertThrows(IllegalArgumentException.class,
                () -> JomlAdapters.finalPositionMatrix(nonAffine),
                "non-affine final matrix");

        Matrix4f nonFinite = new Matrix4f().m21(Float.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> JomlAdapters.finalPositionMatrix(nonFinite),
                "non-finite final matrix");
    }

    private static void canonicalizationAllowsCyclesButNeverReversal() {
        Quad original = manualQuad(List.of(
                vertex(0.0f, 0.0f, 0.0f, 0.1f, 0.2f),
                vertex(1.0f, 0.0f, 0.0f, 0.3f, 0.4f),
                vertex(1.0f, 1.0f, 0.0f, 0.5f, 0.6f),
                vertex(0.0f, 1.0f, 0.0f, 0.7f, 0.8f)));
        Quad rotated = manualQuad(List.of(
                original.vertices().get(2),
                original.vertices().get(3),
                original.vertices().get(0),
                original.vertices().get(1)));
        Quad reversed = manualQuad(List.of(
                original.vertices().get(0),
                original.vertices().get(3),
                original.vertices().get(2),
                original.vertices().get(1)));

        assertEquals(BedrockBoxReplay.canonicalize(original),
                BedrockBoxReplay.canonicalize(rotated), "cyclic starts canonicalize equally");
        assertNotEquals(BedrockBoxReplay.canonicalize(original),
                BedrockBoxReplay.canonicalize(reversed), "reversal must remain distinct");
    }

    private static void canonicalizationUsesRawF32BitsAndMultisetMultiplicity() {
        Quad positiveZero = manualQuad(List.of(
                vertex(0.0f, 0.0f, 0.0f, 0.1f, 0.2f),
                vertex(1.0f, 0.0f, 0.0f, 0.3f, 0.4f),
                vertex(1.0f, 1.0f, 0.0f, 0.5f, 0.6f),
                vertex(0.0f, 1.0f, 0.0f, 0.7f, 0.8f)));
        Quad negativeZero = manualQuad(List.of(
                vertex(-0.0f, 0.0f, 0.0f, 0.1f, 0.2f),
                vertex(1.0f, 0.0f, 0.0f, 0.3f, 0.4f),
                vertex(1.0f, 1.0f, 0.0f, 0.5f, 0.6f),
                vertex(0.0f, 1.0f, 0.0f, 0.7f, 0.8f)));
        assertNotEquals(BedrockBoxReplay.canonicalize(positiveZero),
                BedrockBoxReplay.canonicalize(negativeZero), "raw signed zero is semantic");

        List<CanonicalQuad> one = BedrockBoxReplay.canonicalizeMultiset(List.of(positiveZero));
        List<CanonicalQuad> two = BedrockBoxReplay.canonicalizeMultiset(
                List.of(positiveZero, positiveZero));
        assertEquals(1, one.size(), "single multiset count");
        assertEquals(2, two.size(), "duplicate multiset count");
        assertFalse(Arrays.equals(
                BedrockBoxReplay.canonicalSha256(List.of(positiveZero)),
                BedrockBoxReplay.canonicalSha256(List.of(positiveZero, positiveZero))),
                "digest retains multiplicity");
    }

    private static void inputValidationRejectsOnlyUnsupportedStructuralValues() {
        // These exact-profile signed inputs must all be accepted.
        cube(new Vec3(0.0f, 0.0f, 0.0f), new Vec3(-1.0f, 0.0f, 2.0f),
                -3.0f, false, new Vec2(-4.0f, -5.0f), 1, 1);

        assertThrows(IllegalArgumentException.class, () -> cube(
                new Vec3(0.0f, 0.0f, 0.0f),
                new Vec3(1.0f, 1.0f, 1.0f),
                0.0f,
                false,
                new Vec2(0.0f, 0.0f),
                0,
                1), "zero texture width");
        assertThrows(IllegalArgumentException.class,
                () -> new Vec3(Float.POSITIVE_INFINITY, 0.0f, 0.0f), "non-finite vector");

        EnumMap<Face, Vec3> missing = new EnumMap<>(Face.class);
        missing.put(Face.NORTH, new Vec3(0.0f, 0.0f, -1.0f));
        assertThrows(NullPointerException.class,
                () -> frame(IDENTITY, p -> p, missing), "missing captured normal");
    }

    private static List<Quad> compile(Cube cube, FinalNodeFrame frame) {
        return BedrockBoxReplay.compile(cube, frame, RUNTIME_FIXED_ENDPOINTS, RUNTIME_UV);
    }

    private static Cube cube(
            Vec3 origin,
            Vec3 size,
            float deformation,
            boolean mirror,
            Vec2 boxUv,
            int textureWidth,
            int textureHeight) {
        return new Cube(origin, size, deformation, mirror, boxUv, textureWidth, textureHeight);
    }

    private static FinalNodeFrame frame(
            BoxToMatrixSpace units,
            BedrockBoxReplay.AffinePositionTransform transform,
            Map<Face, Vec3> normals) {
        return new FinalNodeFrame(units, transform, normals);
    }

    private static EnumMap<Face, Vec3> cardinalNormals() {
        EnumMap<Face, Vec3> result = new EnumMap<>(Face.class);
        result.put(Face.NORTH, new Vec3(0.0f, 0.0f, -1.0f));
        result.put(Face.SOUTH, new Vec3(0.0f, 0.0f, 1.0f));
        result.put(Face.EAST, new Vec3(1.0f, 0.0f, 0.0f));
        result.put(Face.WEST, new Vec3(-1.0f, 0.0f, 0.0f));
        result.put(Face.UP, new Vec3(0.0f, 1.0f, 0.0f));
        result.put(Face.DOWN, new Vec3(0.0f, -1.0f, 0.0f));
        return result;
    }

    private static EnumMap<Face, Vec3> distinctiveNormals() {
        EnumMap<Face, Vec3> result = new EnumMap<>(Face.class);
        result.put(Face.NORTH, new Vec3(0.0f, 0.0f, -1.25f));
        result.put(Face.SOUTH, new Vec3(0.0f, 0.0f, 0.75f));
        result.put(Face.EAST, new Vec3(1.10f, 0.0f, 0.0f));
        result.put(Face.WEST, new Vec3(-0.90f, 0.0f, 0.0f));
        result.put(Face.UP, new Vec3(0.0f, 1.40f, 0.0f));
        result.put(Face.DOWN, new Vec3(0.0f, -0.60f, 0.0f));
        return result;
    }

    private static Vec3 unitVectorWithZ(float z) {
        float x = (float) Math.sqrt(1.0D - (double) z * (double) z);
        return new Vec3(x, 0.0f, z);
    }

    private static Quad face(List<Quad> quads, Face face) {
        return quads.stream()
                .filter(quad -> quad.sourceFace() == face)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing face " + face));
    }

    private static void assertFaceSet(List<Quad> quads, Face... expected) {
        List<Face> actualFaces = quads.stream().map(Quad::sourceFace).sorted().toList();
        List<Face> expectedFaces = new ArrayList<>(List.of(expected));
        expectedFaces.sort(null);
        assertEquals(expectedFaces, actualFaces, "face set");
    }

    private static Vec3 firstTriangleCross(Quad quad) {
        Vec3 p0 = quad.vertices().get(0).position();
        Vec3 p1 = quad.vertices().get(1).position();
        Vec3 p2 = quad.vertices().get(2).position();
        return p1.subtract(p0).cross(p2.subtract(p0));
    }

    private static double[] doubleTriangleCross(Vec3 origin, Vec3 first, Vec3 second) {
        double firstX = (double) first.x() - (double) origin.x();
        double firstY = (double) first.y() - (double) origin.y();
        double firstZ = (double) first.z() - (double) origin.z();
        double secondX = (double) second.x() - (double) origin.x();
        double secondY = (double) second.y() - (double) origin.y();
        double secondZ = (double) second.z() - (double) origin.z();
        return new double[] {
            firstY * secondZ - firstZ * secondY,
            firstZ * secondX - firstX * secondZ,
            firstX * secondY - firstY * secondX
        };
    }

    private static boolean isExactZero(double[] vector) {
        return vector[0] == 0.0D && vector[1] == 0.0D && vector[2] == 0.0D;
    }

    private static Vertex vertex(float x, float y, float z, float u, float v) {
        return new Vertex(new Vec3(x, y, z), new Vec2(u, v));
    }

    private static Vertex vertex(Vec3 position) {
        return new Vertex(position, new Vec2(0F, 0F));
    }

    private static Vec3 rawPoint(int x, int y, int z) {
        return new Vec3(
                Float.intBitsToFloat(x), Float.intBitsToFloat(y), Float.intBitsToFloat(z)
        );
    }

    private static Quad manualQuad(List<Vertex> vertices) {
        return new Quad(Face.NORTH, vertices, new Vec3(0.0f, 0.0f, -1.0f));
    }

    private static void assertVecRaw(Vec3 expected, Vec3 actual, String message) {
        assertRaw(expected.x(), actual.x(), message + " x");
        assertRaw(expected.y(), actual.y(), message + " y");
        assertRaw(expected.z(), actual.z(), message + " z");
    }

    private static void assertVec2Raw(Vec2 expected, Vec2 actual, String message) {
        assertRaw(expected.u(), actual.u(), message + " u");
        assertRaw(expected.v(), actual.v(), message + " v");
    }

    private static void assertRaw(float expected, float actual, String message) {
        int expectedBits = Float.floatToRawIntBits(expected);
        int actualBits = Float.floatToRawIntBits(actual);
        if (expectedBits != actualBits) {
            throw new AssertionError(message + ": expected " + expected + " [0x"
                    + Integer.toHexString(expectedBits) + "] but got " + actual + " [0x"
                    + Integer.toHexString(actualBits) + "]");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!ObjectsEqual.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertNotEquals(Object first, Object second, String message) {
        if (ObjectsEqual.equals(first, second)) {
            throw new AssertionError(message + ": values unexpectedly equal " + first);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertThrows(
            Class<? extends Throwable> expectedType, Runnable action, String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected " + expectedType.getName()
                    + " but got " + thrown, thrown);
        }
        throw new AssertionError(message + ": expected " + expectedType.getName());
    }

    /** Avoids importing assertion frameworks in this deliberately dependency-free project. */
    private static final class ObjectsEqual {
        private ObjectsEqual() {
        }

        static boolean equals(Object left, Object right) {
            return left == null ? right == null : left.equals(right);
        }
    }
}
