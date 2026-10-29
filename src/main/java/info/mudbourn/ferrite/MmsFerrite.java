package info.mudbourn.ferrite;

import info.mudbourn.ferrite.command.FerriteCommand;
import info.mudbourn.ferrite.config.FerriteConfig;
import info.mudbourn.ferrite.entity.CrammingDispatcher;
import info.mudbourn.ferrite.monitor.EntityQueryMonitor;
import info.mudbourn.ferrite.monitor.HopperHintMonitor;
import info.mudbourn.ferrite.spatial.ColliderSkip;
import info.mudbourn.ferrite.spatial.EntityCellIndex;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MmsFerrite implements ModInitializer {

    public static final String MOD_ID = "mms_ferrite";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        FerriteConfig.load();
        if (!RustBridge.NATIVE_AVAILABLE) {
            CrammingDispatcher.ENABLED = false;
        }

        EntityQueryMonitor.register();
        HopperHintMonitor.register();
        ColliderSkip.register();
        FerriteCommand.register();
        LOGGER.info(
            "Ferrite backport loaded: native={} cramming={} entityQuery={} colliderSkip={} hopperHint={}",
            RustBridge.NATIVE_AVAILABLE,
            CrammingDispatcher.ENABLED,
            EntityCellIndex.ENABLED,
            ColliderSkip.ENABLED,
            HopperHintMonitor.USE_HINT
        );
    }
}
