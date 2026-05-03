package dev.milow.aaigttracker.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

interface BoardEntry {
    Identifier id();
    Component title();
    ItemStack icon();
    boolean done();
}

record AdvancementBoardEntry(AdvancementSnapshot snapshot) implements BoardEntry {
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

record ItemBoardEntry(ItemProgressSnapshot snapshot) implements BoardEntry {
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

enum TabKind {
    ADVANCEMENTS,
    ITEMS
}

record BoardTab(
        Identifier rootId,
        Component title,
        ItemStack icon,
        List<BoardEntry> entries,
        TabKind kind
) {
}

record TabButton(int index, int x, int y, int width, int height) {
}

record TileHitbox(BoardEntry entry, int x, int y, int width, int height) {
}

record RequirementHitbox(
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
    boolean contains(double mouseX, double mouseY) {
        return TrackerScreenModels.inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
    }

    boolean containsPrimaryIcon(double mouseX, double mouseY) {
        return this.primaryIconWidth > 0
                && this.primaryIconHeight > 0
                && TrackerScreenModels.inside(mouseX, mouseY, this.primaryIconX, this.primaryIconY, this.primaryIconWidth, this.primaryIconHeight);
    }
}

record RequirementViewport(int x, int y, int width, int height, int maxScroll) {
    boolean contains(double mouseX, double mouseY) {
        return TrackerScreenModels.inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
    }
}

record HoveredTile(BoardEntry entry) {
}

record GridLayout(
        int columns,
        int rows,
        int tileWidth,
        int tileHeight,
        float iconScale,
        int iconSize
) {
    int totalWidth() {
        return this.columns * this.tileWidth + Math.max(0, this.columns - 1) * TrackerScreenModels.TILE_GAP;
    }

    int totalHeight() {
        return this.rows * this.tileHeight + Math.max(0, this.rows - 1) * TrackerScreenModels.TILE_GAP;
    }
}

enum RequirementMode {
    SINGLE,
    ONE_OF,
    ALL_OF,
    GROUPED
}

record RequirementSection(Component label, List<CriterionSnapshot> criteria) {
}

record RequirementSectionLayout(
        RequirementSection section,
        int columns,
        int rows,
        int columnWidth,
        int height
) {
}

record RequirementRenderData(
        List<RequirementSectionLayout> layouts,
        int contentWidth,
        int contentHeight,
        int overflowHeight
) {
}

record PreviewOverlay(List<CriterionSnapshot> criteria, int selectedIndex) {
    PreviewOverlay {
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
        if (criteria.isEmpty()) {
            selectedIndex = 0;
        } else {
            selectedIndex = Math.floorMod(selectedIndex, criteria.size());
        }
    }

    CriterionSnapshot current() {
        if (this.criteria.isEmpty()) {
            return null;
        }
        return this.criteria.get(this.selectedIndex);
    }

    boolean hasMultiple() {
        return this.criteria.size() > 1;
    }

    PreviewOverlay navigate(int direction) {
        if (this.criteria.isEmpty()) {
            return this;
        }

        return new PreviewOverlay(this.criteria, this.selectedIndex + direction);
    }
}

record PreviewLayout(
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
    int panelRight() {
        return this.panelX + this.panelWidth;
    }

    int panelBottom() {
        return this.panelY + this.panelHeight;
    }
}

record Rect(int x, int y, int width, int height) {
    int right() {
        return this.x + this.width;
    }

    int bottom() {
        return this.y + this.height;
    }

    boolean contains(double mouseX, double mouseY) {
        return TrackerScreenModels.inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
    }
}

final class TrackerScreenModels {
    static final int TILE_GAP = 6;

    private TrackerScreenModels() {
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
