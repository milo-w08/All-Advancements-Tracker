package dev.milow.aaigttracker.client;

import net.minecraft.resources.Identifier;

public record TrackerUiState(
        CompletionVisibilityMode completionVisibilityMode,
        Identifier lastTrackerRootId,
        Identifier lastSelectedAdvancementId
) {
    public TrackerUiState {
        if (completionVisibilityMode == null) {
            completionVisibilityMode = CompletionVisibilityMode.SHOW_ALL;
        }
    }

    public static TrackerUiState defaults() {
        return new TrackerUiState(CompletionVisibilityMode.SHOW_ALL, null, null);
    }
}
