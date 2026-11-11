package info.mudbourn.ferrite.client;

import net.fabricmc.api.ClientModInitializer;

public class FerriteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WorldLeakFix.register();
    }
}
