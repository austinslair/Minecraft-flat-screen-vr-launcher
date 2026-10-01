package org.vivecraft.client_vr.provider.openxr;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.openxr.EXTPerformanceSettings;
import org.lwjgl.openxr.KHRVisibilityMask;
import org.lwjgl.openxr.XR10;
import org.lwjgl.openxr.XrExtensionProperties;
import org.lwjgl.openxr.XrSession;
import org.lwjgl.system.CustomBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Added to Vivecraft's OpenXR provider by the VoxyQuest launcher; see
 * pojlib.util.VivecraftRefreshRateFix.patchPerformance.
 *
 * Vivecraft never asks Horizon OS for performance levels, so Minecraft ran at the clocks the
 * OS picks for an app that states no needs. This enables XR_EXT_performance_settings when the
 * runtime offers it and requests sustained high CPU and GPU levels once the session exists.
 * Both steps are best effort: any failure leaves Vivecraft's own behaviour unchanged.
 * It also enables XR_KHR_visibility_mask, which VoxyQuestStencil reads the lens-hidden area from.
 */
public final class VoxyQuestXr {
    private VoxyQuestXr() {}

    /** Replaces the flip() that finishes Vivecraft's list of enabled instance extensions. */
    public static CustomBuffer flipExtensions(PointerBuffer names) {
        for (String name : new String[]{EXTPerformanceSettings.XR_EXT_PERFORMANCE_SETTINGS_EXTENSION_NAME,
                KHRVisibilityMask.XR_KHR_VISIBILITY_MASK_EXTENSION_NAME}) {
            try {
                if (names.remaining() > 0 && runtimeOffers(name)) {
                    // Lives as long as the instance; one small allocation per launch.
                    names.put(MemoryUtil.memAddress(MemoryUtil.memUTF8(name)));
                }
            } catch (Throwable ignored) {
                // Keep Vivecraft's list as it was.
            }
        }
        return names.flip();
    }

    /** Called with Vivecraft's session just before it picks the display refresh rate. */
    public static void raisePerformance(XrSession session) {
        try {
            if (session == null || !session.getCapabilities().XR_EXT_performance_settings) return;
            int cpu = EXTPerformanceSettings.xrPerfSettingsSetPerformanceLevelEXT(session,
                    EXTPerformanceSettings.XR_PERF_SETTINGS_DOMAIN_CPU_EXT,
                    EXTPerformanceSettings.XR_PERF_SETTINGS_LEVEL_SUSTAINED_HIGH_EXT);
            int gpu = EXTPerformanceSettings.xrPerfSettingsSetPerformanceLevelEXT(session,
                    EXTPerformanceSettings.XR_PERF_SETTINGS_DOMAIN_GPU_EXT,
                    EXTPerformanceSettings.XR_PERF_SETTINGS_LEVEL_SUSTAINED_HIGH_EXT);
            System.out.println("VoxyQuest: requested sustained high CPU (" + cpu + ") and GPU (" + gpu + ") levels");
        } catch (Throwable ignored) {
            // The runtime keeps its default levels.
        }
    }

    private static boolean runtimeOffers(String extension) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.callocInt(1);
            if (XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, count, null) < 0) return false;
            XrExtensionProperties.Buffer properties = XrExtensionProperties.calloc(count.get(0), stack);
            for (int i = 0; i < properties.capacity(); i++) properties.get(i).type$Default();
            if (XR10.xrEnumerateInstanceExtensionProperties((ByteBuffer) null, count, properties) < 0) return false;
            for (int i = 0; i < properties.capacity(); i++) {
                if (extension.equals(properties.get(i).extensionNameString())) return true;
            }
            return false;
        }
    }
}
