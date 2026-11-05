package dev.kubejseditor.client;

import com.google.gson.JsonObject;
import dev.kubejseditor.recipe.compat.FieldTree;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** A tree and value search, with a distinct action for choosing a wrapper versus opening it. */
public final class FieldPickerScreen extends Screen {
    private final Screen parent;private final JsonObject root;private final Consumer<FieldTree.Node> selected;
    private String scope="",query="",status="展开列表或对象定位字段；搜索路径、原值或类型。";private int offset;
    public FieldPickerScreen(Screen parent,JsonObject source,Consumer<FieldTree.Node> selected){super(Component.literal("选择实际字段"));this.parent=parent;this.root=source.deepCopy();this.selected=selected;}
    @Override protected void init(){
        var search=addRenderableWidget(new EditBox(font,16,34,width-32,20,Component.literal("搜索字段、原值或类型")));search.setMaxLength(4096);search.setValue(query);search.setResponder(text->{query=text;offset=0;rebuildWidgets();});if(!query.isEmpty())setInitialFocus(search);
        button("上一级",16,60,78,()->{scope=FieldTree.parent(scope);offset=0;query="";rebuildWidgets();});button("根目录",98,60,78,()->{scope="";offset=0;query="";rebuildWidgets();});
        var nodes=FieldTree.nodes(root,scope,query);int rows=Math.max(1,(height-154)/30);offset=Math.min(offset,Math.max(0,nodes.size()-rows));
        for(int i=offset;i<Math.min(nodes.size(),offset+rows);i++){
            var node=nodes.get(i);int y=94+(i-offset)*30;String value=node.value().toString();if(value.length()>140)value=value.substring(0,140)+"…";String label=node.path()+" · "+node.hint()+" = "+value;
            var choose=button(font.plainSubstrByWidth(label,width-(node.branch()?126:54)),16,y,width-(node.branch()?104:32),()->{selected.accept(node);if(minecraft.screen==this)minecraft.setScreen(parent);});choose.setTooltip(Tooltip.create(Component.literal(label)));
            if(node.branch())button("展开",width-80,y,64,()->{scope=node.path();query="";offset=0;rebuildWidgets();});
        }
        button("取消",width-96,height-30,80,this::onClose);
    }
    private Button button(String title,int x,int y,int w,Runnable run){return addRenderableWidget(Button.builder(Component.literal(title),b->{try{run.run();}catch(Exception error){status="错误："+error.getMessage();}}).bounds(x,y,w,20).build());}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawString(font,title,16,12,0xFF7DD3FC,false);g.drawString(font,font.plainSubstrByWidth(scope.isEmpty()?"根目录":scope,width-196),184,66,0xFF94A3B8,false);super.render(g,x,y,partial);g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-48,0xFFFFCC88,false);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){offset=Math.max(0,offset-(int)dy*3);rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
