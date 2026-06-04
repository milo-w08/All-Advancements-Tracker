package dev.milow.aaigttracker.mixin;

import dev.milow.aaigttracker.client.app.AllAdvancementsIgtTrackerClient;
import dev.milow.aaigttracker.client.screen.AdvancementTrackerScreen;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    private static final int AAIGTTRACKER_BUTTON_WIDTH = 204;

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void aaigttracker$addTrackerButton(CallbackInfo callbackInfo) {
        Map<Integer, Integer> buttonCountsByRow = new TreeMap<>();
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button button) {
                buttonCountsByRow.merge(button.getY(), 1, Integer::sum);
            }
        }

        Integer advancementRowY = null;
        for (Map.Entry<Integer, Integer> row : buttonCountsByRow.entrySet()) {
            if (row.getValue() >= 2) {
                advancementRowY = row.getKey();
                break;
            }
        }
        if (advancementRowY == null) {
            return;
        }

        int trackerY = advancementRowY + Button.DEFAULT_HEIGHT + Button.DEFAULT_SPACING;
        int shift = Button.DEFAULT_HEIGHT + Button.DEFAULT_SPACING;
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget && widget.getY() > advancementRowY) {
                widget.setY(widget.getY() + shift);
            }
        }

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.aaigttracker.pause_button"),
                        button -> Minecraft.getInstance().setScreen(
                                new AdvancementTrackerScreen(AllAdvancementsIgtTrackerClient.getTrackerManager())
                        )
                )
                .bounds(this.width / 2 - AAIGTTRACKER_BUTTON_WIDTH / 2, trackerY, AAIGTTRACKER_BUTTON_WIDTH, Button.DEFAULT_HEIGHT)
                .build());
    }
}
