package dev.kubejseditor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Consumer;

/** Samples are copies of visible player stacks; no container actions or inventory writes. */
public final class InventorySampleScreen extends Screen {
    private final Screen parent;private final Consumer<ItemStack> accepted;private final List<ItemStack> samples=new ArrayList<>();
    private ItemStack selected=ItemStack.EMPTY;private int left,top,cell;private String status="选择背包、快捷栏、装备或副手物品；取样保留组件。";
    public InventorySampleScreen(Screen parent,Consumer<ItemStack> accepted){super(Component.literal("背包取样"));this.parent=parent;this.accepted=accepted;}
    @Override protected void init(){
        samples.clear();if(minecraft.player!=null)for(int i=0;i<minecraft.player.getInventory().getContainerSize();i++)samples.add(minecraft.player.getInventory().getItem(i).copy());
        cell=Math.max(18,Math.min(24,(height-92)/5));left=(width-cell*9)/2;top=34;
        addRenderableWidget(Button.builder(Component.literal("使用选中样本"),b->{if(selected.isEmpty()){status="请先选择一个非空物品";return;}try{accepted.accept(selected.copy());if(minecraft.screen==this)minecraft.setScreen(parent);}catch(Exception error){status="错误："+error.getMessage();}}).bounds(width/2-140,height-30,136,20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(width/2+4,height-30,136,20).build());
    }
    public ItemStack sample(int index){return samples.get(index).copy();}
    private int at(double x,double y){int col=(int)Math.floor((x-left)/cell),row=(int)Math.floor((y-top)/cell),index=row*9+col;return col<0||col>=9||row<0||index>=samples.size()?-1:index;}
    @Override public boolean mouseClicked(double x,double y,int button){if(super.mouseClicked(x,y,button))return true;int index=at(x,y);if(button==0&&index>=0){selected=sample(index);if(!selected.isEmpty())status=selected.getHoverName().getString()+" ×"+selected.getCount()+"；背包物品不会被消耗";return true;}return false;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawCenteredString(font,title,width/2,12,0xFF7DD3FC);
        for(int i=0;i<samples.size();i++){int sx=left+(i%9)*cell,sy=top+(i/9)*cell;g.fill(sx,sy,sx+cell-1,sy+cell-1,0xFF273448);var stack=samples.get(i);if(!stack.isEmpty()){g.renderItem(stack,sx+(cell-16)/2,sy+(cell-16)/2);g.renderItemDecorations(font,stack,sx+(cell-16)/2,sy+(cell-16)/2);}}
        g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-48,0xFFCBD5E1,false);super.render(g,x,y,partial);int hovered=at(x,y);if(hovered>=0&&!samples.get(hovered).isEmpty())g.renderTooltip(font,samples.get(hovered),x,y);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
