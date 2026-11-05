package dev.kubejseditor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** A non-modal copy-only inventory palette. It never sends container clicks. */
public final class InventorySampleWindow {
    private final Function<Object,List<WorkspaceScreen.GhostTarget>> targets;
    private final Consumer<ItemStack> picked;
    private final List<ItemStack> samples=new ArrayList<>();
    private boolean open,moving,dragging;private int x=-1,y=-1,width=202,height=144,cell=20;private double grabX,grabY,startX,startY;
    private ItemStack held=ItemStack.EMPTY;private String status="拖到物品槽位 · 背包物品不消耗";
    public InventorySampleWindow(Function<Object,List<WorkspaceScreen.GhostTarget>> targets,Consumer<ItemStack> picked){this.targets=targets;this.picked=picked;}
    public boolean isOpen(){return open;}
    public Rect2i bounds(){return new Rect2i(x,y,width,height);}
    public Rect2i slotBounds(int index){return new Rect2i(x+11+index%9*cell,y+25+index/9*cell,cell,cell);}
    public ItemStack sample(int index){return samples.get(index).copy();}
    public void toggle(int screenWidth,int screenHeight){if(open){close();return;}open=true;refresh();if(x<0){x=Math.max(4,screenWidth-width-118);y=Math.max(4,screenHeight-height-40);}resize(screenWidth,screenHeight);}
    public void close(){open=false;moving=dragging=false;held=ItemStack.EMPTY;}
    public void resize(int screenWidth,int screenHeight){if(x<0&&!open)return;x=Math.max(0,Math.min(x,Math.max(0,screenWidth-width)));y=Math.max(0,Math.min(y,Math.max(0,screenHeight-height)));}
    private void refresh(){samples.clear();var player=Minecraft.getInstance().player;if(player!=null)for(int i=0;i<player.getInventory().getContainerSize();i++)samples.add(player.getInventory().getItem(i).copy());}
    private int at(double mx,double my){int col=(int)Math.floor((mx-x-11)/cell),row=(int)Math.floor((my-y-25)/cell),index=row*9+col;return col<0||col>=9||row<0||row>=5||index>=samples.size()?-1:index;}
    public boolean click(double mx,double my,int button){if(!open||!bounds().contains((int)mx,(int)my))return false;if(button!=0)return true;
        if(my<y+21){if(mx>=x+width-22){close();return true;}if(mx>=x+width-46){refresh();return true;}moving=true;grabX=mx-x;grabY=my-y;return true;}
        int index=at(mx,my);if(index>=0&&!samples.get(index).isEmpty()){held=sample(index);startX=mx;startY=my;dragging=false;}return true;
    }
    public boolean drag(double mx,double my,int button,int screenWidth,int screenHeight){if(!open||button!=0)return false;if(moving){x=(int)(mx-grabX);y=(int)(my-grabY);resize(screenWidth,screenHeight);return true;}if(!held.isEmpty()){dragging|=Math.hypot(mx-startX,my-startY)>3;return true;}return false;}
    public boolean release(double mx,double my,int button){if(!open||button!=0)return false;if(moving){moving=false;return true;}if(held.isEmpty())return false;
        var sample=held.copy();boolean dragged=dragging;held=ItemStack.EMPTY;dragging=false;
        try{if(dragged){if(!bounds().contains((int)mx,(int)my))for(var target:targets.apply(sample))if(!target.fluid()&&target.area().contains((int)mx,(int)my)){target.accept().accept(sample.copy());break;}}
            else if(bounds().contains((int)mx,(int)my))picked.accept(sample.copy());
        }catch(Exception error){status="无法放入："+error.getMessage();}return true;
    }
    public boolean scroll(double mx,double my){return open&&bounds().contains((int)mx,(int)my);}
    public void render(GuiGraphics g,int mx,int my){if(!open)return;var mc=Minecraft.getInstance();var font=mc.font;g.pose().pushPose();g.pose().translate(0,0,450);
        if(dragging)for(var target:targets.apply(held))if(!target.fluid()){var r=target.area();g.renderOutline(r.getX()-1,r.getY()-1,r.getWidth()+2,r.getHeight()+2,0xFF7DD3FC);}
        g.fill(x,y,x+width,y+height,0xF51B2838);g.renderOutline(x,y,width,height,0xFF7DD3FC);g.fill(x+1,y+1,x+width-1,y+21,0xFF30445E);
        g.drawString(font,font.plainSubstrByWidth("背包取样 · 拖动标题移动",width-54),x+7,y+7,0xFFE2E8F0,false);g.fill(x+width-44,y+2,x+width-24,y+20,0xFF415976);g.drawString(font,"↻",x+width-39,y+7,0xFFE2E8F0,false);g.drawString(font,"×",x+width-16,y+7,0xFFE2E8F0,false);
        for(int i=0;i<samples.size();i++){var r=slotBounds(i);g.fill(r.getX(),r.getY(),r.getX()+cell-1,r.getY()+cell-1,0xFF101923);var stack=samples.get(i);if(!stack.isEmpty()){g.renderItem(stack,r.getX()+2,r.getY()+2);g.renderItemDecorations(font,stack,r.getX()+2,r.getY()+2);}}
        g.drawString(font,font.plainSubstrByWidth(status,width-12),x+6,y+height-13,0xFFCBD5E1,false);
        if(!held.isEmpty()){g.pose().translate(0,0,50);g.renderItem(held,mx-8,my-8);g.renderItemDecorations(font,held,mx-8,my-8);}
        else{int index=at(mx,my);if(index>=0&&!samples.get(index).isEmpty())g.renderTooltip(font,samples.get(index),mx,my);}
        g.pose().popPose();
    }
}
