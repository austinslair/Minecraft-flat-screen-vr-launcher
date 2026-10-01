package dev.simulated_team.simulated.util.hold_interaction;

/** Shaped like Simulated's manager: a private static active interaction and a static tick. */
public class HoldInteractionManager {
    private static BlockHoldInteraction active = null;
    public static int ticks;

    public static void start(final BlockHoldInteraction interaction) {
        active = interaction;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static void stop() {
        active = null;
    }

    public static void tick(final Object level, final Object player) {
        ticks++;
    }
}
