package dev.kubejseditor.client;

import com.google.gson.JsonObject;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.math.BigDecimal;
import java.util.*;

public final class LootTableScreen extends LootVisualScreen {
    private record Cell(LootEntries.Entry entry, Rect2i area, ItemStack icon) {}
    private final Screen parent;
    public final EditorProject.RecipeDraft draft;
    private final List<Cell> cells = new ArrayList<>();
    private String query = "";
    private int rowOffset, maximumOffset;
    private Rect2i addTarget;

    public LootTableScreen(Screen parent, RegistryEntryTarget target) {
        super(Component.literal("掉落表 · " + target.entry)); this.parent = parent;
        var source = EditorRuntime.recipe(target.id());
        if (source == null || source.data() == null) throw new IllegalArgumentException(source == null ? "掉落表不存在" : source.error());
        var saved = EditorRuntime.project().recipes.getOrDefault(target.id(), EditorRuntime.state().applied.recipes.get(target.id()));
        draft = saved == null ? new EditorProject.RecipeDraft(target.id(), source.data()) : WorkspaceState.copy(saved, EditorProject.RecipeDraft.class);
        if (saved == null) { draft.source = source.data().deepCopy(); draft.registryTarget = source.registryTarget(); draft.operation = "replace"; draft.origin = "existing"; }
        draft.forceNative = true;
        status = "拖入空位新增，拖到已有掉落替换；点击物品修改数量和概率。";
    }
    @Override protected void init() {
        super.init(); cells.clear(); int content = contentWidth();
        var search = addRenderableWidget(new EditBox(font, 16, 34, content-32, 20, Component.literal("搜索物品或字段")));
        search.setValue(query); search.setResponder(text -> { query = text; rowOffset = 0; rebuildWidgets(); });
        if (!query.isEmpty()) setInitialFocus(search);
        int control = (content-44)/4;
        button("新增掉落", 16, 60, control, () -> edit(null));
        addTarget = new Rect2i(16, 60, control, 20);
        button("背包取样", 20+control, 60, control, this::openInventory);
        button("编辑 JSON", 24+control*2, 60, control, () -> minecraft.setScreen(new CompatValueScreen(this,
            "完整掉落表 JSON", ProjectStore.JSON.toJson(draft.data), text -> stage(dev.kubejseditor.script.ValueDocument.parse(text).getAsJsonObject()))));
        button("字段补丁", 28+control*3, 60, control, () -> minecraft.setScreen(new FieldPatchScreen(this, draft.data, this::stage)));
        var entries = LootEntries.items(draft.data).stream().filter(e -> (e.item()+e.path()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int columns = Math.max(1, (content-24)/200), cardWidth = (content-32-(columns-1)*8)/columns;
        int rows = Math.max(1, (height-160)/64), totalRows = (entries.size()+1+columns-1)/columns;
        maximumOffset = Math.max(0, totalRows-rows); rowOffset = Math.min(rowOffset, maximumOffset);
        for (int i = rowOffset*columns; i < Math.min(entries.size()+1, (rowOffset+rows)*columns); i++) {
            var entry = i == entries.size() ? null : entries.get(i);
            int x = 16+(i%columns)*(cardWidth+8), y = 90+(i/columns-rowOffset)*64;
            cells.add(new Cell(entry, new Rect2i(x, y, cardWidth, 58), entry == null ? ItemStack.EMPTY : LootItemStacks.preview(draft.data, entry)));
            if (entry != null) button("×", x+cardWidth-24, y+3, 20, () -> stage(LootEntries.remove(draft.data, entry.path())));
        }
        button("一键应用", 16, height-30, 90, this::apply);
        button("导出数据包", 110, height-30, 96, () -> minecraft.setScreen(new DatapackExportScreen(this, draft)));
        button("返回", content-78, height-30, 62, this::onClose);
    }
    public void stage(JsonObject next) {
        EditorRuntime.requireEditable(); EditorRuntime.validatePatch(draft, next);
        var copy = WorkspaceState.copy(draft, EditorProject.RecipeDraft.class); copy.data = next.deepCopy();
        var previous = EditorRuntime.project().recipes.put(draft.id, copy);
        try { EditorRuntime.saveDraft(); } catch (Exception error) {
            if (previous == null) EditorRuntime.project().recipes.remove(draft.id); else EditorRuntime.project().recipes.put(draft.id, previous);
            throw new IllegalStateException(error);
        }
        draft.data = next.deepCopy(); status = "已加入待办；一键应用后生效。共享此掉落表的使用者都会受影响。";
        rebuildWidgets();
    }
    private void place(ItemStack stack, LootEntries.Entry entry) {
        if (stack.isEmpty()) return;
        stage(LootEntries.edit(draft.data, entry == null ? null : entry.path(),
            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), entry == null ? "1" : "",
            entry == null ? "1" : "", entry == null ? "1" : "", LootItemStacks.components(stack)));
    }
    @Override protected void samplePicked(ItemStack stack) { action(() -> place(stack, null)); }
    @Override protected List<WorkspaceScreen.GhostTarget> itemTargets(ItemStack stack) {
        var targets = new ArrayList<WorkspaceScreen.GhostTarget>();
        if (addTarget != null) targets.add(new WorkspaceScreen.GhostTarget(addTarget, false,
            value -> { if (value instanceof ItemStack sample) action(() -> place(sample, null)); }));
        for (var cell : cells) {
            var r = cell.area();
            targets.add(new WorkspaceScreen.GhostTarget(new Rect2i(r.getX(), r.getY(), r.getWidth()-26, r.getHeight()), false,
                value -> { if (value instanceof ItemStack sample) action(() -> place(sample, cell.entry())); }));
        }
        return targets;
    }
    private void edit(LootEntries.Entry entry) { minecraft.setScreen(new LootEntryScreen(this, draft.data, entry, this::stage)); }
    private void apply() throws java.io.IOException {
        var review = EditorRuntime.review(); EditorRuntime.apply(review);
        if (review.restartRequired()) { status = "同批包含启动期修改，请重启生效"; return; }
        EditorRuntime.reloadResult(review.resourcesChanged(), result -> { status = result.message(); rebuildWidgets(); });
        status = "正在重载并核对掉落表";
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0) for (var cell : cells) if (cell.area().contains((int)x, (int)y)) { action(() -> edit(cell.entry())); return true; }
        return false;
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (super.mouseScrolled(x, y, dx, dy)) return true;
        if (x < 16 || x >= contentWidth()-16 || y < 90 || y >= height-66) return false;
        rowOffset = Math.max(0, Math.min(maximumOffset, rowOffset-(int)Math.signum(dy))); rebuildWidgets(); return true;
    }
    private static String chance(String value) {
        return value.isBlank() ? "原规则" : new BigDecimal(value).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString()+"%";
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        g.fill(0, 0, width, height, 0xFF101923);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), contentWidth()-32), 16, 12, 0xFF7DD3FC, false);
        for (var cell : cells) {
            var r = cell.area(); int sx = r.getX(), sy = r.getY(); boolean hovered = r.contains(x, y) && !overInventory(x, y);
            g.fill(sx, sy, sx+r.getWidth(), sy+r.getHeight(), hovered ? 0xFF30445E : 0xFF1B2838);
            g.renderOutline(sx, sy, r.getWidth(), r.getHeight(), hovered ? 0xFF7DD3FC : 0xFF415976);
            if (cell.entry() == null) { g.drawString(font, "+ 拖入物品新增掉落", sx+10, sy+23, 0xFF7DD3FC, false); continue; }
            g.fill(sx+6, sy+8, sx+34, sy+36, 0xFF101923);
            if (!cell.icon().isEmpty()) g.renderItem(cell.icon(), sx+12, sy+14);
            var e = cell.entry(); String name = cell.icon().isEmpty() ? e.item() : cell.icon().getHoverName().getString();
            g.drawString(font, font.plainSubstrByWidth(name, r.getWidth()-68), sx+40, sy+9, 0xFFE2E8F0, false);
            String count = e.minimum().isBlank() ? "原规则" : e.minimum().equals(e.maximum()) ? e.minimum() : e.minimum()+"～"+e.maximum();
            g.drawString(font, font.plainSubstrByWidth("数量 "+count, r.getWidth()-48), sx+40, sy+25, 0xFFCBD5E1, false);
            g.drawString(font, font.plainSubstrByWidth("基础概率 "+chance(e.chance()), r.getWidth()-16), sx+8, sy+43, 0xFF94A3B8, false);
        }
        super.render(g, x, y, partial);
        g.drawString(font, font.plainSubstrByWidth(status, contentWidth()-32), 16, height-48, 0xFFFFCC88, false);
        if (!overInventory(x, y)) for (var cell : cells) if (cell.area().contains(x, y) && !cell.icon().isEmpty()) g.renderTooltip(font, cell.icon(), x, y);
        renderInventory(g, x, y);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
