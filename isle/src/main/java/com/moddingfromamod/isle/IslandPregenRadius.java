package com.moddingfromamod.isle;

import com.moddingfromamod.builder.BuildShape;
import com.moddingfromamod.event.shape.FallShape;
import com.moddingfromamod.runtime.EventFraming;
import com.moddingfromamod.runtime.Framing;
import com.moddingfromamod.runtime.OrbitCamera;
import com.moddingfromamod.wish.WishAim;
import com.moddingfromamod.wish.WishSpotShot;

/**
 * <b>How far round an island's centre the idle pre-generation reaches</b> (#2542) — Minecraft-free, and
 * DERIVED from the constants that decide where a child's wish lands and where the camera is sent to film
 * it, never typed. A child standing on his new island can aim a wish {@link #aimReach()} away; a build there
 * reaches {@link #buildExtent()} further; the camera stands up to {@link #cameraStandoff()} from what it
 * films; and the camera's client is then sent every chunk within the server's view distance of where it
 * stands. Ungenerated terrain anywhere inside that is generated ON DEMAND while somebody waits, floating on
 * invisible ground.
 *
 * <p>{@code IslandPregenRadiusTest} pins the result: a raised framing constant changes it, and that test
 * fails by name with the new radius and its disk cost, rather than the camera quietly flying into
 * ungenerated terrain or the pre-generation quietly doubling its disk.
 */
public final class IslandPregenRadius {

    /**
     * Where he stands is not quite the centre: the starter template is about a chunk across, and the
     * stand block is on it. One chunk of slack covers it.
     */
    public static final int STAND_SLACK = 16;

    private IslandPregenRadius() {
    }

    /** The farthest a wish's spot may be aimed from where he stands: the watch's reach, or a fall's aim. */
    public static double aimReach() {
        return Math.max(WishAim.REACH, FallShape.MAX_AIM);
    }

    /** The farthest a build reaches from its anchor: its largest offset plus its largest dimension. */
    public static double buildExtent() {
        return BuildShape.MAX_OFFSET + BuildShape.MAX_DIM;
    }

    /**
     * The farthest the camera is put from what it films: a creature's portrait ({@link Framing}), an
     * event ({@link EventFraming}), a building's orbit and film ({@link OrbitCamera#MAX_BUILD_DISTANCE},
     * which {@code BuildFraming}'s outside shots and {@code film/CameraPath}'s orbits stand at), and the
     * wish-spot shot ({@link WishSpotShot}). An interior shot ({@code BuildFraming}) is inside the build,
     * so within {@link #buildExtent()}.
     */
    public static double cameraStandoff() {
        return Math.max(Math.max(Framing.MAX_DISTANCE, EventFraming.MAX_DISTANCE),
                Math.max(OrbitCamera.MAX_BUILD_DISTANCE, WishSpotShot.MAX_FROM));
    }

    /** The farthest, in blocks from the island's centre, the camera can be sent to stand. */
    public static double cameraReachBlocks() {
        return STAND_SLACK + aimReach() + buildExtent() + cameraStandoff();
    }

    /**
     * The radius in chunks (a square, Chebyshev) to generate round an island's centre chunk: the chunk
     * the camera can stand in, plus the view distance the server sends round it (a client is never sent
     * more than the server's view distance, so the camera's own setting cannot widen it), plus that
     * chunk itself. It is always at least the child's own view distance.
     */
    public static int radiusChunks(int viewDistanceChunks) {
        int vd = Math.max(0, viewDistanceChunks);
        return (int) Math.ceil(cameraReachBlocks() / 16.0) + vd + 1;
    }

    /** Chunks in one cell's square of this radius. */
    public static long chunksPerCell(int radiusChunks) {
        long side = 2L * radiusChunks + 1;
        return side * side;
    }

    /**
     * A region file keeps each chunk in whole 4 KiB sectors, and a sky-void chunk fits one or two: the
     * disk an un-overlapped cell costs, in MiB, at {@link #BYTES_PER_CHUNK}. An estimate for the plan; the
     * live report reads the region folder itself.
     */
    public static double estimatedMiBPerCell(int radiusChunks) {
        return chunksPerCell(radiusChunks) * BYTES_PER_CHUNK / (1024.0 * 1024.0);
    }

    /** Two sectors: what a generated sky-void chunk with its lighting and heightmaps takes. */
    public static final int BYTES_PER_CHUNK = 8192;
}
