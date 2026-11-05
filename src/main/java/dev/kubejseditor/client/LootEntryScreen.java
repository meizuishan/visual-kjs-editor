package dev.kubejseditor.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.kubejseditor.recipe.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public final class LootEntryScreen extends Screen {
    private final Screen parent;private final JsonObject table;private final LootEntries.Entry entry;private final Consumer<JsonObject> accepted;
    private String item,min,max,chance,status="空白数量／概率表示保留原结构；基础概率仍受其他掉落条件影响。";private JsonObject components;
    public LootEntryScreen(Screen parent,JsonObject table,LootEntries.Entry entry,Consumer<JsonObject> accepted){super(Component.literal(entry==null?"新增物品掉落":"修改物品掉落"));this.parent=parent;this.table=table.deepCopy();this.entry=entry;this.accepted=accepted;item=entry==null?"minecraft:iron_ingot":entry.item();min=entry==null?"1":entry.minimum();max=entry==null?"1":entry.maximum();chance=entry==null?"1":entry.chance();}
    @Override protected void init(){int x=Math.max(16,width/2-180),w=Math.min(360,width-32);
        box(x,48,w,"物品 ID",item,v->item=v);box(x,92,(w-8)/2,"数量下限",min,v->min=v);box(x+(w+8)/2,92,(w-8)/2,"数量上限",max,v->max=v);box(x,136,w,"独立基础概率 0～1",chance,v->chance=v);
        addRenderableWidget(Button.builder(Component.literal("背包取样（保留组件）"),b->minecraft.setScreen(new InventorySampleScreen(this,stack->{item=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();var encoded=net.minecraft.world.item.ItemStack.CODEC.encodeStart(minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),stack).getOrThrow().getAsJsonObject();components=encoded.has("components")?encoded.getAsJsonObject("components").deepCopy():new JsonObject();minecraft.setScreen(this);}))).bounds(x,170,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("检查并保存"),b->{try{var id=net.minecraft.resources.ResourceLocation.parse(item.trim());if(!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("未知物品："+id);accepted.accept(LootEntries.edit(table,entry==null?null:entry.path(),item.trim(),min.trim(),max.trim(),chance.trim(),components));minecraft.setScreen(parent);}catch(Exception error){status="错误："+error.getMessage();}}).bounds(x,height-30,130,20).build());addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(x+w-90,height-30,90,20).build());
    }
    private void box(int x,int y,int w,String title,String value,Consumer<String> change){var input=addRenderableWidget(new EditBox(font,x,y,w,20,Component.literal(title)));input.setMaxLength(1024);input.setValue(value);input.setResponder(change);input.setTooltip(Tooltip.create(Component.literal(title)));}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawString(font,title,16,12,0xFF7DD3FC,false);int left=Math.max(16,width/2-180),w=Math.min(360,width-32);g.drawString(font,"物品 ID",left,36,0xFFCBD5E1,false);g.drawString(font,"数量下限／上限（空白保留）",left,80,0xFFCBD5E1,false);g.drawString(font,"独立基础概率 0～1（空白保留）",left,124,0xFFCBD5E1,false);g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-48,0xFFFFCC88,false);super.render(g,x,y,partial);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
