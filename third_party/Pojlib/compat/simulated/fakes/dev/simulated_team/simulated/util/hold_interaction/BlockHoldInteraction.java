package dev.simulated_team.simulated.util.hold_interaction;

public abstract class BlockHoldInteraction {
    public void release() {}

    public boolean activeOnMouseMove(final double yaw, final double pitch) {
        return false;
    }
}
