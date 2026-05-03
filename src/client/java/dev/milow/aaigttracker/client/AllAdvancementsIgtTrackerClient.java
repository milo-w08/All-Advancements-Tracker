package dev.milow.aaigttracker.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.milow.aaigttracker.AllAdvancementsIgtTracker;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class AllAdvancementsIgtTrackerClient implements ClientModInitializer {
    private static AdvancementTrackerManager trackerManager;
    private static KeyMapping openTrackerKey;
    private static KeyMapping cycleVisibilityModeKey;
    private static final Map<WorldKey, TrackerUiState> trackerUiStates = new HashMap<>();

    @Override
    public void onInitializeClient() {
        trackerManager = new AdvancementTrackerManager();
        KeyMapping.Category trackerCategory = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(AllAdvancementsIgtTracker.MOD_ID, "tracker")
        );

        openTrackerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aaigttracker.open_screen",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_WORLD_1,
                trackerCategory
        ));
        cycleVisibilityModeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.aaigttracker.cycle_visibility_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_MINUS,
                trackerCategory
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            trackerManager.tick(client);

            while (openTrackerKey.consumeClick()) {
                if (client.player == null) {
                    continue;
                }

                if (client.screen instanceof AdvancementTrackerScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new AdvancementTrackerScreen(trackerManager));
                }
            }

        });

        AllAdvancementsIgtTracker.LOGGER.info("Registered client hooks for {}", AllAdvancementsIgtTracker.MOD_ID);
    }

    public static AdvancementTrackerManager getTrackerManager() {
        return trackerManager;
    }

    public static CompletionVisibilityMode getCompletionVisibilityMode(WorldKey worldKey) {
        return getTrackerUiState(worldKey).completionVisibilityMode();
    }

    public static void cycleCompletionVisibilityMode(WorldKey worldKey) {
        if (worldKey == null) {
            return;
        }

        TrackerUiState currentState = getTrackerUiState(worldKey);
        trackerUiStates.put(
                worldKey,
                new TrackerUiState(
                        currentState.completionVisibilityMode().next(),
                        currentState.lastTrackerRootId(),
                        currentState.lastSelectedAdvancementId()
                )
        );
    }

    public static KeyMapping getCycleVisibilityModeKey() {
        return cycleVisibilityModeKey;
    }

    public static KeyMapping getOpenTrackerKey() {
        return openTrackerKey;
    }

    public static void rememberTrackerState(
            WorldKey worldKey,
            CompletionVisibilityMode completionVisibilityMode,
            Identifier rootId,
            Identifier selectedAdvancementId
    ) {
        if (worldKey == null) {
            return;
        }

        trackerUiStates.put(
                worldKey,
                new TrackerUiState(completionVisibilityMode, rootId, selectedAdvancementId)
        );
    }

    public static TrackerUiState getTrackerUiState(WorldKey worldKey) {
        if (worldKey == null) {
            return TrackerUiState.defaults();
        }

        return trackerUiStates.computeIfAbsent(worldKey, ignored -> TrackerUiState.defaults());
    }
}
