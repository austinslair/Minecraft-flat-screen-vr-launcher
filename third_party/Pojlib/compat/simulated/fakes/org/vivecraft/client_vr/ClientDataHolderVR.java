package org.vivecraft.client_vr;

import org.vivecraft.client_vr.gameplay.VRPlayer;

public class ClientDataHolderVR {
    private static final ClientDataHolderVR INSTANCE = new ClientDataHolderVR();
    public VRPlayer vrPlayer = new VRPlayer();

    public static ClientDataHolderVR getInstance() {
        return INSTANCE;
    }
}
