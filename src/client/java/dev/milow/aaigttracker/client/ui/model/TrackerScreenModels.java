package dev.milow.aaigttracker.client.ui.model;

import dev.milow.aaigttracker.client.model.AdvancementSnapshot;
import dev.milow.aaigttracker.client.model.CriterionSnapshot;
import dev.milow.aaigttracker.client.model.ItemProgressSnapshot;
import dev.milow.aaigttracker.client.waypoint.WaypointSnapshot;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class TrackerScreenModels {
    public static final int TILE_GAP = 6;

    private TrackerScreenModels() {
    }

    public interface BoardEntry {
        Identifier id();

        Component title();

        ItemStack icon();

        boolean done();
    }

    public record AdvancementBoardEntry(AdvancementSnapshot snapshot) implements BoardEntry {
        @Override
        public Identifier id() {
            return this.snapshot.id();
        }

        @Override
        public Component title() {
            return this.snapshot.title();
        }

        @Override
        public ItemStack icon() {
            return this.snapshot.icon().copy();
        }

        @Override
        public boolean done() {
            return this.snapshot.done();
        }
    }

    public record ItemBoardEntry(ItemProgressSnapshot snapshot) implements BoardEntry {
        @Override
        public Identifier id() {
            return this.snapshot.id();
        }

        @Override
        public Component title() {
            return this.snapshot.title();
        }

        @Override
        public ItemStack icon() {
            return this.snapshot.icon().copy();
        }

        @Override
        public boolean done() {
            return this.snapshot.done();
        }
    }

    public record WaypointBoardEntry(WaypointSnapshot snapshot) implements BoardEntry {
        @Override
        public Identifier id() {
            return this.snapshot.id();
        }

        @Override
        public Component title() {
            return this.snapshot.title();
        }

        @Override
        public ItemStack icon() {
            return this.snapshot.icon();
        }

        @Override
        public boolean done() {
            return false;
        }
    }

    public enum TabKind {
        ADVANCEMENTS,
        ITEMS,
        WAYPOINTS
    }

    public record BoardTab(
            Identifier rootId,
            Component title,
            ItemStack icon,
            List<BoardEntry> entries,
            TabKind kind
    ) {
    }

    public record TabButton(int index, int x, int y, int width, int height) {
    }

    public record TileHitbox(BoardEntry entry, int x, int y, int width, int height) {
    }

    public record RequirementHitbox(
            CriterionSnapshot criterion,
            int x,
            int y,
            int width,
            int height,
            int primaryIconX,
            int primaryIconY,
            int primaryIconWidth,
            int primaryIconHeight
    ) {
        public boolean contains(double mouseX, double mouseY) {
            return TrackerScreenModels.inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }

        public boolean containsPrimaryIcon(double mouseX, double mouseY) {
            return this.primaryIconWidth > 0
                    && this.primaryIconHeight > 0
                    && TrackerScreenModels.inside(mouseX, mouseY, this.primaryIconX, this.primaryIconY, this.primaryIconWidth, this.primaryIconHeight);
        }
    }

    public record RequirementViewport(int x, int y, int width, int height, int maxScroll) {
        public boolean contains(double mouseX, double mouseY) {
            return TrackerScreenModels.inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }

    public record HoveredTile(BoardEntry entry) {
    }

    public record GridLayout(
            int columns,
            int rows,
            int tileWidth,
            int tileHeight,
            float iconScale,
            int iconSize
    ) {
        public int totalWidth() {
            return this.columns * this.tileWidth + Math.max(0, this.columns - 1) * TrackerScreenModels.TILE_GAP;
        }

        public int totalHeight() {
            return this.rows * this.tileHeight + Math.max(0, this.rows - 1) * TrackerScreenModels.TILE_GAP;
        }
    }

    public enum RequirementMode {
        SINGLE,
        ONE_OF,
        ALL_OF,
        GROUPED
    }

    public record RequirementSection(Component label, List<CriterionSnapshot> criteria) {
    }

    public record RequirementSectionLayout(
            RequirementSection section,
            int columns,
            int rows,
            int columnWidth,
            int height
    ) {
    }

    public record RequirementRenderData(
            List<RequirementSectionLayout> layouts,
            int contentWidth,
            int contentHeight,
            int overflowHeight
    ) {
    }

    public record PreviewOverlay(List<CriterionSnapshot> criteria, int selectedIndex) {
        public PreviewOverlay {
            criteria = criteria == null ? List.of() : List.copyOf(criteria);
            if (criteria.isEmpty()) {
                selectedIndex = 0;
            } else {
                selectedIndex = Math.floorMod(selectedIndex, criteria.size());
            }
        }

        public CriterionSnapshot current() {
            if (this.criteria.isEmpty()) {
                return null;
            }
            return this.criteria.get(this.selectedIndex);
        }

        public boolean hasMultiple() {
            return this.criteria.size() > 1;
        }

        public PreviewOverlay navigate(int direction) {
            if (this.criteria.isEmpty()) {
                return this;
            }

            return new PreviewOverlay(this.criteria, this.selectedIndex + direction);
        }
    }

    public record PreviewLayout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int visualX,
            int visualY,
            int visualWidth,
            int visualHeight,
            Rect closeButton,
            Rect leftArrow,
            Rect rightArrow
    ) {
        public int panelRight() {
            return this.panelX + this.panelWidth;
        }

        public int panelBottom() {
            return this.panelY + this.panelHeight;
        }
    }

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return this.x + this.width;
        }

        public int bottom() {
            return this.y + this.height;
        }

        public boolean contains(double mouseX, double mouseY) {
            return TrackerScreenModels.inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
