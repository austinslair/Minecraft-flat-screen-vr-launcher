package dev.simulated_team.simulated.util.hold_interaction;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Added to Simulated (Create Aeronautics) by the VoxyQuest launcher; see pojlib.util.SimulatedVrFix.
 *
 * The physics assembler lever, throttle lever and steering wheel are moved by holding use and
 * moving the mouse, and let go when the mouse button is released. In VR neither happens:
 * Vivecraft aims with the headset and presses the use key directly. Every client tick this
 * feeds the main controller's rotation to the held control as mouse movement, at the same
 * scale as mouse look, and releases the control once the use key is let go.
 *
 * Minecraft and Vivecraft are reached by reflection so that one class serves both NeoForge
 * (Mojang names) and Fabric (intermediary names), and does nothing when VR is not running.
 */
public final class VoxyQuestVrHold {
    /** Degrees of view rotation per unit of mouse movement (LocalPlayer.turn). */
    private static final double DEGREES_PER_UNIT = 0.15;

    private static boolean resolved, available;
    private static Field vrRunning, vrPlayer, roomPre, mainHand, options, keyUse;
    private static Method holder, pitch, yaw, minecraft, isDown;

    private static BlockHoldInteraction tracked;
    private static float lastPitch, lastYaw;

    private VoxyQuestVrHold() {}

    /** Called at the start of HoldInteractionManager.tick with the active interaction, or null. */
    public static void tick(final BlockHoldInteraction active) {
        if (active == null) {
            tracked = null;
            return;
        }
        try {
            if (!resolve() || !vrRunning.getBoolean(null)) return;
            if (!isDown.invoke(keyUse.get(options.get(minecraft.invoke(null)))).equals(Boolean.TRUE)) {
                // What BlockHoldInteraction.onUse does when the mouse button is released.
                tracked = null;
                active.release();
                HoldInteractionManager.stop();
                return;
            }
            final Object pose = mainHand.get(roomPre.get(vrPlayer.get(holder.invoke(null))));
            if (pose == null) return;
            final float currentPitch = (Float) pitch.invoke(pose);
            final float currentYaw = (Float) yaw.invoke(pose);
            if (active != tracked) {
                tracked = active;
                lastPitch = currentPitch;
                lastYaw = currentYaw;
                return;
            }
            float yawChange = currentYaw - lastYaw;
            if (yawChange > 180) yawChange -= 360;
            if (yawChange < -180) yawChange += 360;
            // Vivecraft's pitch is positive upward, Minecraft's view pitch is positive downward.
            final float pitchChange = -(currentPitch - lastPitch);
            lastPitch = currentPitch;
            lastYaw = currentYaw;
            if (yawChange != 0 || pitchChange != 0) {
                active.activeOnMouseMove(yawChange / DEGREES_PER_UNIT, pitchChange / DEGREES_PER_UNIT);
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            available = false; // An unexpected Vivecraft or Minecraft build: leave desktop behaviour alone.
        }
    }

    private static boolean resolve() {
        if (resolved) return available;
        resolved = true;
        try {
            final ClassLoader loader = VoxyQuestVrHold.class.getClassLoader();
            vrRunning = Class.forName("org.vivecraft.client_vr.VRState", false, loader).getField("VR_RUNNING");
            final Class<?> holderClass = Class.forName("org.vivecraft.client_vr.ClientDataHolderVR", false, loader);
            holder = holderClass.getMethod("getInstance");
            vrPlayer = holderClass.getField("vrPlayer");
            roomPre = vrPlayer.getType().getField("vrdata_room_pre");
            mainHand = roomPre.getType().getField("c0");
            pitch = mainHand.getType().getMethod("getPitch");
            yaw = mainHand.getType().getMethod("getYaw");
            Class<?> game;
            try {
                game = Class.forName("net.minecraft.client.Minecraft", false, loader);
                minecraft = game.getMethod("getInstance");
                options = game.getField("options");
                keyUse = options.getType().getField("keyUse");
                isDown = keyUse.getType().getMethod("isDown");
            } catch (final ClassNotFoundException fabric) {
                game = Class.forName("net.minecraft.class_310", false, loader);
                minecraft = game.getMethod("method_1551");
                options = game.getField("field_1690");
                keyUse = options.getType().getField("field_1904");
                isDown = keyUse.getType().getMethod("method_1434");
            }
            available = true;
        } catch (final ReflectiveOperationException | LinkageError e) {
            available = false; // Vivecraft is not installed.
        }
        return available;
    }
}
