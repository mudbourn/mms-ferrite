package info.mudbourn.ferrite.client;

import info.mudbourn.ferrite.MmsFerrite;
import info.mudbourn.ferrite.mixin.client.EntityRenderDispatcherAccessor;
import info.mudbourn.ferrite.mixin.client.LivingEntityRendererAccessor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

// Releases client worlds that other mods keep reachable after the player leaves them.
public final class WorldLeakFix {

    public static volatile boolean ENABLED = true;

    private static final String LEAWIND_AIMING =
        "io.github.leawind.thirdperson.internal.logic.scheduler.aiming.MinecraftItemPredicateIntegration";
    private static final String WTHIT_ACCESSOR = "mcp.mobius.waila.access.ClientAccessor";
    private static final String[] WTHIT_WORLD_FIELDS = {"world", "player", "entity", "blockEntity", "hitResult"};

    private static final Field SKYBOXIFY_LEVEL = field("skyboxify", LevelRenderer.class.getName(), "skyboxify$level");
    private static final Field CHAT_PATCHES_BACKUP = field("chatpatches", "obro1961.chatpatches.util.VersionUtil", "backup");
    private static final Field LEAWIND_CONNECTION = field("leawind_third_person", LEAWIND_AIMING, "compiledConnection");
    private static final Field LEAWIND_PREDICATES = field("leawind_third_person", LEAWIND_AIMING, "predicates");
    private static final Method LEAWIND_EMPTY_PREDICATES = method("leawind_third_person", LEAWIND_AIMING + "$CompiledPredicates", "empty");
    private static final Field ABSOLUT_REVIVE_CONTROLLER = field(
        "absolutrevive",
        "goetic.mods.absolutrevive.client.ClientEventHandler",
        "SUPPRESSION_FEEDBACK_CONTROLLER"
    );
    private static final Field ABSOLUT_REVIVE_LEVEL = field(
        "absolutrevive",
        "goetic.mods.absolutrevive.client.SuppressionFeedbackController",
        "trackedLevel"
    );
    private static final Field WTHIT_INSTANCE = field("wthit", WTHIT_ACCESSOR, "INSTANCE");
    private static final Field[] WTHIT_FIELDS = fields("wthit", WTHIT_ACCESSOR, WTHIT_WORLD_FIELDS);
    private static final Field ARMOR_RENDER_STATE = field("fabric-rendering-v1", HumanoidArmorLayer.class.getName(), "bipedRenderState");

    private WorldLeakFix() {
    }

    public static void register() {
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> onWorldChange(client, level));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> onDisconnect(client)));
    }

    private static void onWorldChange(Minecraft client, ClientLevel level) {
        if (!ENABLED) {
            return;
        }
        set(null, SKYBOXIFY_LEVEL, level);
        if (level != null) {
            set(null, CHAT_PATCHES_BACKUP, level);
        }
        resetLeawind();
        clearArmorRenderStates(client);
    }

    private static void onDisconnect(Minecraft client) {
        if (!ENABLED || client.level != null) {
            return;
        }
        set(null, SKYBOXIFY_LEVEL, null);
        resetLeawind();
        set(get(ABSOLUT_REVIVE_CONTROLLER), ABSOLUT_REVIVE_LEVEL, null);
        Object wthit = get(WTHIT_INSTANCE);
        for (Field field : WTHIT_FIELDS) {
            set(wthit, field, null);
        }
        clearArmorRenderStates(client);
    }

    private static void resetLeawind() {
        if (LEAWIND_CONNECTION == null || LEAWIND_PREDICATES == null || LEAWIND_EMPTY_PREDICATES == null) {
            return;
        }
        try {
            LEAWIND_PREDICATES.set(null, LEAWIND_EMPTY_PREDICATES.invoke(null));
            LEAWIND_CONNECTION.set(null, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            MmsFerrite.LOGGER.warn("Could not reset Leawind's compiled connection: {}", e.toString());
        }
    }

    private static void clearArmorRenderStates(Minecraft client) {
        if (ARMOR_RENDER_STATE == null) {
            return;
        }
        EntityRenderDispatcherAccessor dispatcher = (EntityRenderDispatcherAccessor) client.getEntityRenderDispatcher();
        clearArmorRenderStates(dispatcher.ferrite$renderers());
        clearArmorRenderStates(dispatcher.ferrite$playerRenderers());
        clearArmorRenderStates(dispatcher.ferrite$mannequinRenderers());
    }

    private static void clearArmorRenderStates(Map<?, ?> renderers) {
        if (renderers == null) {
            return;
        }
        for (Object renderer : renderers.values()) {
            if (!(renderer instanceof LivingEntityRendererAccessor living)) {
                continue;
            }
            List<RenderLayer<?, ?>> layers = living.ferrite$layers();
            for (RenderLayer<?, ?> layer : layers) {
                if (layer instanceof HumanoidArmorLayer<?, ?, ?>) {
                    set(layer, ARMOR_RENDER_STATE, null);
                }
            }
        }
    }

    private static Object get(Field field) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            MmsFerrite.LOGGER.warn("Could not read {}.{}: {}", field.getDeclaringClass().getName(), field.getName(), e.toString());
            return null;
        }
    }

    private static void set(Object owner, Field field, Object value) {
        if (field == null || (owner == null && !Modifier.isStatic(field.getModifiers()))) {
            return;
        }
        try {
            field.set(owner, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            MmsFerrite.LOGGER.warn("Could not reset {}.{}: {}", field.getDeclaringClass().getName(), field.getName(), e.toString());
        }
    }

    private static Field[] fields(String modId, String className, String[] names) {
        Field[] found = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
            found[i] = field(modId, className, names[i]);
        }
        return found;
    }

    private static Field field(String modId, String className, String name) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) {
            return null;
        }
        try {
            Field field = Class.forName(className, false, WorldLeakFix.class.getClassLoader()).getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            MmsFerrite.LOGGER.warn("World leak fix for {} is off, {}.{} not found: {}", modId, className, name, e.toString());
            return null;
        }
    }

    private static Method method(String modId, String className, String name) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) {
            return null;
        }
        try {
            Method method = Class.forName(className, false, WorldLeakFix.class.getClassLoader()).getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            MmsFerrite.LOGGER.warn("World leak fix for {} is off, {}.{}() not found: {}", modId, className, name, e.toString());
            return null;
        }
    }
}
