package dev.kubejseditor.client;

import com.google.gson.JsonObject;
import dev.kubejseditor.recipe.LootEntries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.function.Consumer;

public final class LootEntryScreen extends LootVisualScreen {
    private final Screen parent;
    private final JsonObject table;
    private final LootEntries.Entry entry;
    private final Consumer<JsonObject> accepted;
    private ItemStack selected;
    private String min, max, chance;
    private JsonObject components;
    private Rect2i slot;

    public LootEntryScreen(Screen parent, JsonObject table, LootEntries.Entry entry, Consumer<JsonObject> accepted) {
        super(Component.literal(entry == null ? "新增物品掉落" : "修改物品掉落"));
        this.parent = parent; this.table = table.deepCopy(); this.entry = entry; this.accepted = accepted;
        selected = entry == null ? ItemStack.EMPTY : LootItemStacks.preview(table, entry);
        min = entry == null ? "1" : entry.minimum(); max = entry == null ? "1" : entry.maximum();
        chance = entry == null ? "1" : entry.chance();
        status = "拖入物品后设置数量和基础概率；其他掉落条件保留。";
    }
    @Override protected void init() {
        super.init(); int w = contentWidth() - 32;
        button("背包取样", 16, 34, 100, this::openInventory);
        button("清空物品", 120, 34, 90, () -> { selected = ItemStack.EMPTY; components = null; });
        slot = new Rect2i(16, 64, 36, 36);
        box(16, 120, (w - 8) / 2, "数量下限", min, v -> min = v);
        box(16 + (w + 8) / 2, 120, (w - 8) / 2, "数量上限", max, v -> max = v);
        box(16, 164, w, "独立基础概率 0～1", chance, v -> chance = v);
        button("检查并保存", 16, height - 30, 112, this::save);
        button("取消", contentWidth() - 96, height - 30, 80, this::onClose);
    }
    private void box(int x, int y, int w, String name, String value, Consumer<String> change) {
        var input = addRenderableWidget(new EditBox(font, x, y, w, 20, Component.literal(name)));
        input.setMaxLength(1024); input.setValue(value); input.setResponder(change);
        input.setTooltip(Tooltip.create(Component.literal(name)));
    }
    @Override protected void samplePicked(ItemStack stack) {
        if (stack.isEmpty()) return;
        var sampled = LootItemStacks.components(stack);
        selected = stack.copy(); selected.setCount(1); components = sampled;
        status = "已选择 " + selected.getHoverName().getString() + "；设置数量后保存";
    }
    @Override protected List<WorkspaceScreen.GhostTarget> itemTargets(ItemStack stack) {
        return slot == null ? List.of() : List.of(new WorkspaceScreen.GhostTarget(slot, false,
            value -> { if (value instanceof ItemStack sample) action(() -> samplePicked(sample)); }));
    }
    private void save() {
        if (selected.isEmpty()) throw new IllegalArgumentException("请先从 JEI 或背包拖入掉落物品");
        boolean countsUnchanged = entry != null && min.trim().equals(entry.minimum()) && max.trim().equals(entry.maximum());
        boolean chanceUnchanged = entry != null && chance.trim().equals(entry.chance());
        accepted.accept(LootEntries.edit(table, entry == null ? null : entry.path(),
            BuiltInRegistries.ITEM.getKey(selected.getItem()).toString(),
            countsUnchanged ? "" : min.trim(), countsUnchanged ? "" : max.trim(),
            chanceUnchanged ? "" : chance.trim(), components));
        minecraft.setScreen(parent);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (slot.contains((int)x, (int)y)) {
            if (button == 0) openInventory();
            else if (button == 1) { selected = ItemStack.EMPTY; components = null; }
            return true;
        }
        return false;
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        g.fill(0, 0, width, height, 0xFF101923);
        g.drawString(font, title, 16, 12, 0xFF7DD3FC, false);
        g.fill(slot.getX(), slot.getY(), slot.getX()+36, slot.getY()+36, 0xFF273448);
        g.renderOutline(slot.getX(), slot.getY(), 36, 36, 0xFF7DD3FC);
        if (selected.isEmpty()) g.drawString(font, "+", 30, 78, 0xFF7DD3FC, false);
        else g.renderItem(selected, 26, 74);
        String name = selected.isEmpty() ? "从 JEI 或背包拖入物品" : selected.getHoverName().getString();
        g.drawString(font, font.plainSubstrByWidth(name, contentWidth()-78), 62, 68, 0xFFE2E8F0, false);
        String hint = selected.isEmpty() ? "点击槽位打开背包取样" : BuiltInRegistries.ITEM.getKey(selected.getItem()).toString();
        g.drawString(font, font.plainSubstrByWidth(hint, contentWidth()-78), 62, 86, 0xFF94A3B8, false);
        g.drawString(font, "数量下限／上限（空白保留）", 16, 108, 0xFFCBD5E1, false);
        g.drawString(font, "独立基础概率 0～1（空白保留）", 16, 152, 0xFFCBD5E1, false);
        super.render(g, x, y, partial);
        g.drawString(font, font.plainSubstrByWidth(status, contentWidth()-32), 16, height-48, 0xFFFFCC88, false);
        if (!overInventory(x, y) && slot.contains(x, y) && !selected.isEmpty()) g.renderTooltip(font, selected, x, y);
        renderInventory(g, x, y);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
