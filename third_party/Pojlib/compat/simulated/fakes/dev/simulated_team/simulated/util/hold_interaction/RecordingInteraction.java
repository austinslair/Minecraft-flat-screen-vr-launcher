package dev.simulated_team.simulated.util.hold_interaction;

public class RecordingInteraction extends BlockHoldInteraction {
    public double yaw, pitch;
    public int moves, releases;

    @Override public void release() {
        releases++;
    }

    @Override public boolean activeOnMouseMove(final double yaw, final double pitch) {
        this.yaw += yaw;
        this.pitch += pitch;
        moves++;
        return true;
    }
}
