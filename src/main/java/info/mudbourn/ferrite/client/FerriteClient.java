package info.mudbourn.ferrite.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class FerriteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WorldLeakFix.register();
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> BobbyLightStore.retainOnly(level));
        ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> client.execute(() -> BobbyLightStore.retainOnly(client.level))
        );
    }
}
