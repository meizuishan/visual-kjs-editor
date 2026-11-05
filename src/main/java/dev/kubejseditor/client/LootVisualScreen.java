package dev.kubejseditor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import dev.kubejseditor.runtime.EditorRuntime;
import java.util.List;

/** Shared copy-only item dragging for loot tables and individual drops. */
public abstract class LootVisualScreen extends Screen {
    @FunctionalInterface protected interface Operation { void run() throws Exception; }
    protected String status = "";
    private final InventorySampleWindow inventory = new InventorySampleWindow(this::dropTargets, this::samplePicked);

    protected LootVisualScreen(Component title) { super(title); }
    public final int contentWidth() { return width >= 400 ? width - 112 : width; }
    public final InventorySampleWindow inventoryWindow() { return inventory; }
    public final void openInventory() { inventory.toggle(contentWidth(), height); }
    protected abstract List<WorkspaceScreen.GhostTarget> itemTargets(ItemStack stack);
    protected abstract void samplePicked(ItemStack stack);

    public final List<WorkspaceScreen.GhostTarget> dropTargets(Object value) {
        return value instanceof ItemStack stack && !stack.isEmpty() && !EditorRuntime.locked()
            ? itemTargets(stack.copy()) : List.of();
    }
    protected final void action(Operation operation) {
        try { operation.run(); } catch (Exception error) { status = "错误：" + error.getMessage(); }
    }
    protected final Button button(String text, int x, int y, int w, Operation operation) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action(operation))
            .bounds(x, y, w, 20).build());
    }
    @Override protected void init() { inventory.resize(contentWidth(), height); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        return inventory.click(x, y, button) || super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return inventory.drag(x, y, button, contentWidth(), height) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return inventory.release(x, y, button) || super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return inventory.scroll(x, y) || super.mouseScrolled(x, y, dx, dy);
    }
    protected final void renderInventory(GuiGraphics g, int x, int y) { inventory.render(g, x, y); }
    protected final boolean overInventory(int x, int y) {
        return inventory.isOpen() && inventory.bounds().contains(x, y);
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float partial) {}
    @Override public boolean isPauseScreen() { return false; }
}
