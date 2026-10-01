package org.vivecraft.client_vr.provider.openxr;

import java.lang.reflect.Field;
import java.nio.IntBuffer;
import org.lwjgl.openxr.KHRVisibilityMask;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrFovf;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.openxr.XrVector2f;
import org.lwjgl.openxr.XrView;
import org.lwjgl.openxr.XrVisibilityMaskKHR;
import org.lwjgl.system.MemoryStack;

/**
 * Added to Vivecraft's OpenXR provider by the VoxyQuest launcher; see
 * pojlib.util.VivecraftStencilFix.
 *
 * Vivecraft skips shading the parts of each eye image the lenses hide when its VR runtime
 * supplies a hidden-area mesh, but its OpenXR renderer never asked for one, so every pixel of
 * both eyes was shaded. This reads the mesh through XR_KHR_visibility_mask (enabled by
 * VoxyQuestXr.flipExtensions) and hands it to Vivecraft in its pixel format. Any failure, or a
 * mesh that does not look like lens corners, leaves Vivecraft drawing everything as before.
 */
public final class VoxyQuestStencil {
    /** A real hidden area is the image edges; more than this or the centre means a bad mesh. */
    private static final float MAX_HIDDEN = 0.35f;

    private static boolean failed;
    /** Why the last mesh was rejected; printed so a headset log shows it. */
    private static String reason = "";
    private static float[][] masks;
    private static int builtWidth;
    private static int builtHeight;
    private static final float[][] builtTangents = new float[2][];
    private static Field session;
    private static Field views;
    private static Field width;
    private static Field height;

    private VoxyQuestStencil() {}

    /** Replaces OpenXRStereoRenderer.providesStencilMask; {@code openxr} is its MCOpenXR. */
    public static boolean provides(Object openxr) {
        return masks(openxr) != null;
    }

    /** Replaces getStencilMask(RenderPass) for the OpenXR renderer. */
    public static float[] mask(Object openxr, Object pass) {
        String name = pass instanceof Enum ? ((Enum<?>) pass).name() : "";
        int eye = "LEFT".equals(name) ? 0 : "RIGHT".equals(name) ? 1 : -1;
        float[][] all = eye < 0 ? null : masks(openxr);
        return all == null ? null : all[eye];
    }

    private static synchronized float[][] masks(Object openxr) {
        if (failed || openxr == null) return null;
        try {
            if (session == null) {
                Class<?> type = openxr.getClass();
                session = type.getField("session");
                views = type.getField("viewBuffer");
                width = type.getField("width");
                height = type.getField("height");
            }
            XrSession xrSession = (XrSession) session.get(openxr);
            XrView.Buffer viewBuffer = (XrView.Buffer) views.get(openxr);
            int w = width.getInt(openxr);
            int h = height.getInt(openxr);
            if (xrSession == null || viewBuffer == null || viewBuffer.capacity() < 2 || w <= 0 || h <= 0) return null;
            if (!xrSession.getCapabilities().XR_KHR_visibility_mask) {
                failed = true;
                System.out.println("VoxyQuest: XR_KHR_visibility_mask is not enabled; shading full eye images");
                return null;
            }
            float[][] tangents = {tangents(viewBuffer.get(0).fov()), tangents(viewBuffer.get(1).fov())};
            // The views are located every frame; before the first one there is no field of view yet.
            if (tangents[0] == null || tangents[1] == null) return null;
            if (masks != null && w == builtWidth && h == builtHeight &&
                    same(tangents[0], builtTangents[0]) && same(tangents[1], builtTangents[1])) return masks;
            float[][] built = new float[2][];
            float[] hidden = new float[2];
            for (int eye = 0; eye < 2; eye++) {
                built[eye] = build(xrSession, eye, tangents[eye], w, h, hidden);
                if (built[eye] == null) {
                    failed = true;
                    System.out.println("VoxyQuest: the runtime's visibility mask was not usable (eye " + eye + ": "
                            + reason + "); shading full eye images");
                    return null;
                }
            }
            if (masks == null) System.out.println(String.format(
                    "VoxyQuest: skipping the lens-hidden area, %.0f%% / %.0f%% of each eye", hidden[0] * 100, hidden[1] * 100));
            masks = built;
            builtWidth = w;
            builtHeight = h;
            builtTangents[0] = tangents[0];
            builtTangents[1] = tangents[1];
            return masks;
        } catch (Throwable e) {
            failed = true;
            System.out.println("VoxyQuest: visibility mask failed: " + e);
            return null;
        }
    }

    /** left, right, down, up tangents, or null for a field of view that is not set yet. */
    private static float[] tangents(XrFovf fov) {
        float[] t = {(float) Math.tan(fov.angleLeft()), (float) Math.tan(fov.angleRight()),
                (float) Math.tan(fov.angleDown()), (float) Math.tan(fov.angleUp())};
        return t[1] - t[0] > 0.01f && t[3] - t[2] > 0.01f ? t : null;
    }

    private static boolean same(float[] a, float[] b) {
        if (b == null) return false;
        for (int i = 0; i < 4; i++) if (Math.abs(a[i] - b[i]) > 1e-4f) return false;
        return true;
    }

    /** Triangles as x, y pairs in swapchain pixels with y up, as Vivecraft's drawMask expects. */
    private static float[] build(XrSession xrSession, int eye, float[] t, int w, int h, float[] hidden) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrVisibilityMaskKHR mask = XrVisibilityMaskKHR.calloc(stack).type(KHRVisibilityMask.XR_TYPE_VISIBILITY_MASK_KHR);
            int type = XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
            int hiddenMesh = KHRVisibilityMask.XR_VISIBILITY_MASK_TYPE_HIDDEN_TRIANGLE_MESH_KHR;
            int status = KHRVisibilityMask.xrGetVisibilityMaskKHR(xrSession, type, eye, hiddenMesh, mask);
            if (status < 0) { reason = "xrGetVisibilityMaskKHR returned " + status; return null; }
            int vertexCount = mask.vertexCountOutput();
            int indexCount = mask.indexCountOutput();
            if (vertexCount <= 0 || indexCount < 3 || indexCount % 3 != 0) {
                reason = vertexCount + " vertices, " + indexCount + " indices";
                return null;
            }
            XrVector2f.Buffer vertices = XrVector2f.malloc(vertexCount, stack);
            IntBuffer indices = stack.mallocInt(indexCount);
            mask.vertexCapacityInput(vertexCount).vertices(vertices)
                    .indexCapacityInput(indexCount).indices(indices);
            status = KHRVisibilityMask.xrGetVisibilityMaskKHR(xrSession, type, eye, hiddenMesh, mask);
            if (status < 0) { reason = "xrGetVisibilityMaskKHR returned " + status; return null; }

            // Vertices lie on the z = -1 plane of the view, so x and y are tangents of the view angle.
            float[] result = new float[indexCount * 2];
            float area = 0;
            float cx = w / 2f;
            float cy = h / 2f;
            for (int i = 0; i < indexCount; i += 3) {
                for (int k = 0; k < 3; k++) {
                    int index = indices.get(i + k);
                    if (index < 0 || index >= vertexCount) { reason = "index " + index + " out of range"; return null; }
                    XrVector2f v = vertices.get(index);
                    result[(i + k) * 2] = (v.x() - t[0]) / (t[1] - t[0]) * w;
                    result[(i + k) * 2 + 1] = (v.y() - t[2]) / (t[3] - t[2]) * h;
                }
                int o = i * 2;
                float x0 = result[o], y0 = result[o + 1], x1 = result[o + 2], y1 = result[o + 3],
                        x2 = result[o + 4], y2 = result[o + 5];
                area += Math.abs((x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)) / 2f;
                if (contains(x0, y0, x1, y1, x2, y2, cx, cy)) {
                    reason = String.format("covers the centre; first vertex (%.3f, %.3f), view tangents %.3f..%.3f / %.3f..%.3f",
                            vertices.get(0).x(), vertices.get(0).y(), t[0], t[1], t[2], t[3]);
                    return null;
                }
            }
            hidden[eye] = area / ((float) w * h);
            if (hidden[eye] > 0 && hidden[eye] <= MAX_HIDDEN) return result;
            reason = String.format("hides %.0f%% of the eye (%d vertices, %d indices)",
                    hidden[eye] * 100, vertexCount, indexCount);
            return null;
        }
    }

    private static boolean contains(float x0, float y0, float x1, float y1, float x2, float y2, float px, float py) {
        float d1 = (px - x1) * (y0 - y1) - (x0 - x1) * (py - y1);
        float d2 = (px - x2) * (y1 - y2) - (x1 - x2) * (py - y2);
        float d3 = (px - x0) * (y2 - y0) - (x2 - x0) * (py - y0);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
        boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }
}
