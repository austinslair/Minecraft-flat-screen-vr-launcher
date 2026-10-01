package net.minecraft.client;

public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public final Options options = new Options();

    public static Minecraft getInstance() {
        return INSTANCE;
    }
}
