package mchorse.bbs_mod.ui.utils;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draw the gizmo with parallel projection, centred on its scene-camera pixel.
 * Its viewing direction runs from the camera position to the gizmo, with the
 * scene's up axis. Panning the camera does not turn the tool, and its near and
 * far sides have the same scale.
 *
 * <p>The virtual depth is the original distance, preserving the existing size
 * compensation. The visual, pick stencil, highlight and screen projections all
 * use this lens; world-space dragging continues to use the scene camera.
 */
public class GizmoLens
{
    /** Reference angle for the existing size compensation. The orthographic
     *  footprint matches the old lens at the gizmo's centre depth. */
    public final static float FOV = (float) Math.toRadians(30);

    /** Below this a projected point is on or behind the camera plane and there is
     *  nothing to frame; the gizmo isn't drawn at all in that case. */
    private final static float CLIP_EPSILON = 1.0E-4F;

    /** Parallel projection shifted onto the gizmo's camera pixel.
     *  The camera's own projection while the lens is inactive. */
    public final Matrix4f projection = new Matrix4f();

    /** View adjustment prepended to the gizmo's model-view, putting it on the eye axis.
     *  Identity while the lens is inactive. */
    public final Matrix4f viewDelta = new Matrix4f();

    /** Factor the gizmo's distance scale is multiplied by to undo the narrow
     *  frustum's zoom. {@code 1} while the lens is inactive. */
    public float scale = 1F;

    /** Whether the swaps above are anything but identity. */
    public boolean active;

    /**
     * Whether a gizmo at {@code gizmoViewPosition} (its model-view's translation,
     * i.e. its place in view space) can be framed by a lens under
     * {@code cameraProjection}. All visual and picking passes use this predicate.
     *
     * <p>An orthographic camera is deliberately excluded: it has no perspective to
     * shear the gizmo in the first place, so its existing projection is kept.
     */
    public static boolean canFrame(Matrix4f cameraProjection, Vector3f gizmoViewPosition)
    {
        if (cameraProjection == null || gizmoViewPosition == null)
        {
            return false;
        }

        /* m33 != 0 is an orthographic projection; m11 is 1 / tan(fov / 2). */
        if (cameraProjection.m33() != 0F || cameraProjection.m11() == 0F || cameraProjection.m00() == 0F)
        {
            return false;
        }

        Vector4f clip = cameraProjection.transform(new Vector4f(gizmoViewPosition, 1F));

        return clip.w > CLIP_EPSILON;
    }

    /**
     * Look at the target with scene up, expressed in camera coordinates. Using
     * camera up here would introduce roll as the camera pans across the target.
     * Without a camera frame, only recenter the origin.
     */
    public static boolean viewDelta(Vector3f gizmoViewPosition, Matrix4f cameraView, Matrix4f out)
    {
        float distance = gizmoViewPosition.length();

        if (distance < CLIP_EPSILON)
        {
            return false;
        }

        if (cameraView == null)
        {
            out.translation(-gizmoViewPosition.x, -gizmoViewPosition.y, -distance - gizmoViewPosition.z);
        }
        else
        {
            Vector3f direction = new Vector3f(gizmoViewPosition).div(distance);
            Vector3f up = cameraView.transformDirection(new Vector3f(0F, 1F, 0F)).normalize();

            /* At a scene-up pole use scene Z, not a camera-dependent fallback. */
            if (direction.cross(up, new Vector3f()).lengthSquared() < 1.0E-8F)
            {
                cameraView.transformDirection(up.set(0F, 0F, 1F)).normalize();
            }

            out.setLookAlong(direction, up);
        }

        return true;
    }

    /**
     * Build the lens for a gizmo drawn with {@code gizmoModelView} under
     * {@code cameraProjection}. Returns whether it came out active; when it did not,
     * the fields hold the identity swap (the camera's own projection, no view adjustment,
     * no rescale) so callers can use them unconditionally.
     */
    public boolean set(Matrix4f cameraProjection, Matrix4f gizmoModelView, Matrix4f cameraView)
    {
        this.projection.set(cameraProjection);
        this.viewDelta.identity();
        this.scale = 1F;
        this.active = false;

        Vector3f position = gizmoModelView.getTranslation(new Vector3f());

        if (!canFrame(cameraProjection, position) || !viewDelta(position, cameraView, this.viewDelta))
        {
            this.viewDelta.identity();

            return false;
        }

        Vector4f clip = cameraProjection.transform(new Vector4f(position, 1F));
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float distance = position.length();
        float tanCamera = 1F / cameraProjection.m11();
        float tanLens = (float) Math.tan(FOV / 2F);

        /* Preserve the old scale at the centre, but remove the depth-dependent
         * division that made tilted rings asymmetric and cubes trapezoidal.
         * Keep depth for sorting the handles against each other. The infinite
         * constraint guide uses the scene camera, outside this projection. */
        float halfHeight = distance * tanLens;
        float halfWidth = halfHeight * cameraProjection.m11() / cameraProjection.m00();
        float depthRange = Math.max(distance, 1F) * 16F;

        this.projection
            .setOrtho(-halfWidth, halfWidth, -halfHeight, halfHeight, -depthRange, depthRange)
            .m30(ndcX)
            .m31(ndcY);

        this.scale = tanLens / tanCamera;
        this.active = true;

        return true;
    }
}
