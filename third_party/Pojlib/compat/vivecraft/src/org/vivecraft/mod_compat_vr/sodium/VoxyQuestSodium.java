package org.vivecraft.mod_compat_vr.sodium;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Added to Vivecraft by the VoxyQuest launcher; see pojlib.util.VivecraftSodiumFix.
 *
 * Vivecraft's VR arms reuse the player skin by copying texture coordinates between cube faces.
 * With Sodium installed the faces Minecraft draws come from Sodium's ModelCuboid, and Sodium 0.8
 * keeps each face's coordinates in a packed {@code long[] textures} array (four entries per face,
 * in Minecraft's face order), which Vivecraft does not know. Vivecraft then logs "VR hands will
 * probably look wrong" and leaves the arms with the wrong part of the skin. This copies the face
 * in that layout and returns false for any other Sodium, so Vivecraft's own code handles it.
 */
public final class VoxyQuestSodium {
    private static final int FACE_VERTICES = 4;
    private static volatile boolean unsupported;
    private static Field cubes;
    private static Field cuboid;
    private static Field textures;

    private VoxyQuestSodium() {}

    /** Called first in SodiumHelper.copyModelCuboidUV with its arguments; true when the copy is done. */
    public static boolean copyUV(Object source, Object dest, int sourcePoly, int destPoly) {
        if (unsupported) return false;
        try {
            long[] from = textures(source);
            long[] to = textures(dest);
            if (from == null || to == null || sourcePoly < 0 || destPoly < 0 ||
                    (sourcePoly + 1) * FACE_VERTICES > from.length || (destPoly + 1) * FACE_VERTICES > to.length) {
                return false;
            }
            System.arraycopy(from, sourcePoly * FACE_VERTICES, to, destPoly * FACE_VERTICES, FACE_VERTICES);
            return true;
        } catch (Throwable e) {
            unsupported = true;
            return false;
        }
    }

    private static synchronized long[] textures(Object part) throws ReflectiveOperationException {
        if (cubes == null) cubes = listField(part.getClass());
        List<?> list = (List<?>) cubes.get(part);
        if (list == null || list.isEmpty()) return null;
        Object cube = list.get(0);
        if (cuboid == null) {
            // Sodium's mixin field on ModelPart.Cube; Vivecraft looks it up by the same name.
            cuboid = cube.getClass().getDeclaredField("sodium$cuboid");
            cuboid.setAccessible(true);
            Field field = cuboid.getType().getDeclaredField("textures");
            if (field.getType() != long[].class) throw new NoSuchFieldException("textures");
            field.setAccessible(true);
            textures = field;
        }
        Object model = cuboid.get(cube);
        return model == null ? null : (long[]) textures.get(model);
    }

    /** ModelPart's cube list, found by type since its name differs between mappings. */
    private static Field listField(Class<?> type) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType() == List.class && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException("cubes");
    }
}
