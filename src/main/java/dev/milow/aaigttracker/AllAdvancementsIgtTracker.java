package dev.milow.aaigttracker;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AllAdvancementsIgtTracker implements ModInitializer {
    public static final String MOD_ID = "aaigttracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing {}", MOD_ID);
    }
}