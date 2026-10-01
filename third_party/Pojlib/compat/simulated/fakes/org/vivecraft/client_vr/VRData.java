package org.vivecraft.client_vr;

public class VRData {
    public VRDevicePose c0 = new VRDevicePose();

    public static class VRDevicePose {
        public float pitch, yaw;

        public float getPitch() {
            return pitch;
        }

        public float getYaw() {
            return yaw;
        }
    }
}
