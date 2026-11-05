package dev.kubejseditor.client;

import com.google.gson.JsonObject;
import dev.kubejseditor.recipe.compat.NumericParameters;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;

/** A numeric draft stays local until the whole document passes native validation. */
public final class NumericParametersScreen extends Screen {
    private final Screen parent;
    private final JsonObject original;
    private final Consumer<JsonObject> accepted;
    private final List<NumericParameters.Field> fields;
    private final Map<String,String> changes=new LinkedHashMap<>();
    private String query="",status="数值与数字字符串保留原类型；检查并保存后加入待办。";
    private int offset;
    private List<NumericParameters.Field> visible=List.of();
    public NumericParametersScreen(Screen parent,JsonObject source,Consumer<JsonObject> accepted){
        super(Component.literal("数值参数"));this.parent=parent;this.original=source.deepCopy();this.accepted=accepted;this.fields=NumericParameters.fields(source);
    }
    @Override protected void init(){
        var search=addRenderableWidget(new EditBox(font,16,34,width-32,20,Component.literal("搜索路径或数值")));search.setMaxLength(4096);search.setValue(query);
        search.setResponder(text->{query=text;offset=0;rebuildWidgets();});
        if(!query.isEmpty())setInitialFocus(search);
        String filter=query.toLowerCase(Locale.ROOT);
        visible=fields.stream().filter(f->(f.path()+" "+changes.getOrDefault(f.path(),f.value())).toLowerCase(Locale.ROOT).contains(filter)).toList();
        int rows=Math.max(1,(height-128)/38);offset=Math.min(offset,Math.max(0,visible.size()-rows));
        for(int i=offset;i<Math.min(visible.size(),offset+rows);i++){
            var field=visible.get(i);int y=64+(i-offset)*38;
            var input=addRenderableWidget(new EditBox(font,16,y+12,width-32,18,Component.literal(field.path())));input.setMaxLength(256);input.setValue(changes.getOrDefault(field.path(),field.value()));
            input.setResponder(text->{if(text.equals(field.value()))changes.remove(field.path());else changes.put(field.path(),text);});
            input.setTooltip(Tooltip.create(Component.literal(field.path()+(field.stringEncoded()?"\n数字字符串：保留引号":"\nJSON 数值"))));
        }
        addRenderableWidget(Button.builder(Component.literal("检查并保存"),b->save()).bounds(16,height-30,110,20).build());
        addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(width-96,height-30,80,20).build());
    }
    private void save(){try{accepted.accept(NumericParameters.apply(original,changes));minecraft.setScreen(parent);}catch(Exception error){status="错误："+error.getMessage();}}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(hasControlDown()&&key==GLFW.GLFW_KEY_S){save();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        g.fill(0,0,width,height,0xFF101923);g.drawString(font,title,16,12,0xFF7DD3FC,false);super.render(g,x,y,partial);
        int rows=Math.max(1,(height-128)/38);for(int i=offset;i<Math.min(visible.size(),offset+rows);i++){var f=visible.get(i);g.drawString(font,font.plainSubstrByWidth(f.path()+(f.stringEncoded()?" · 字符串":""),width-36),16,64+(i-offset)*38,0xFFCBD5E1,false);}
        if(visible.isEmpty())g.drawString(font,"没有匹配的数值字段",16,68,0xFF94A3B8,false);
        g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-48,0xFFFFCC88,false);
        if(status.startsWith("错误")&&y>=height-52&&y<height-32)g.renderTooltip(font,font.split(Component.literal(status),Math.max(80,width-40)),x,y);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){offset=Math.max(0,offset-(int)dy*3);rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
