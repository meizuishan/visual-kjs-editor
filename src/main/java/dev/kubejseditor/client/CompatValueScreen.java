package dev.kubejseditor.client;

import dev.kubejseditor.script.ValueDocument;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.*;

/** Exact field input with a persistent local draft, strict JSON tools and reversible text edits. */
public final class CompatValueScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> save;
    private final ValueDocument document;
    private final boolean json;
    private final Map<String,Function<String,String>> previews;
    private String preview="";
    private MultiLineEditBox input;
    private String status="Ctrl+S 保存 · Ctrl+Shift+F 格式化 · Ctrl+Z/Y 撤销/重做";
    private String checked="",syntax="";
    public CompatValueScreen(Screen parent,String title,String initial,Consumer<String> save){
        this(parent,title,initial,ValueDocument.structured(initial),save);
    }
    public CompatValueScreen(Screen parent,String title,String initial,boolean json,Consumer<String> save){
        this(parent,title,initial,json,save,Map.of());
    }
    public CompatValueScreen(Screen parent,String title,String initial,boolean json,Consumer<String> save,Map<String,Function<String,String>> previews){
        super(Component.literal(title));this.parent=parent;this.save=save;this.document=new ValueDocument(initial);this.json=json;this.previews=new LinkedHashMap<>(previews);
    }
    @Override protected void init(){
        int tools=34;
        if(!previews.isEmpty()){var names=new ArrayList<String>();names.add("原始 JSON");names.addAll(previews.keySet());int tw=(width-32-4*(names.size()-1))/names.size();for(int i=0;i<names.size();i++){String name=names.get(i),key=i==0?"":name;addRenderableWidget(Button.builder(Component.literal((preview.equals(key)?"● ":"")+name),b->{preview=key;rebuildWidgets();}).bounds(16+i*(tw+4),34,tw,20).build());}tools=62;}
        boolean writable=preview.isEmpty()&&save!=null;
        int w=Math.max(30,(width-44)/4);
        addRenderableWidget(Button.builder(Component.literal("格式化"),b->format()).bounds(16,tools,w,20).build()).active=writable;
        addRenderableWidget(Button.builder(Component.literal("复制全文"),b->{minecraft.keyboardHandler.setClipboard(input.getValue());status="已原样复制当前文本";}).bounds(20+w,tools,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("撤销"),b->input.setValue(document.undo())).bounds(24+w*2,tools,w,20).build()).active=writable;
        addRenderableWidget(Button.builder(Component.literal("重做"),b->input.setValue(document.redo())).bounds(28+w*3,tools,w,20).build()).active=writable;
        input=new MultiLineEditBox(font,16,tools+28,width-32,Math.max(20,height-tools-96),Component.literal("输入 JSON 或字段值"),Component.literal("字段值"));
        String visible=document.text();if(!preview.isEmpty())try{visible=previews.get(preview).apply(document.text());}catch(Exception error){visible="无法预览："+error.getMessage();}
        input.setCharacterLimit(ValueDocument.MAX_LENGTH);input.setValue(visible);input.setValueListener(text->{if(preview.isEmpty()&&save!=null)document.change(text);});addRenderableWidget(input);setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("检查并保存"),b->save()).bounds(16,height-30,110,20).build()).active=writable;
        addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(width-96,height-30,80,20).build());
    }
    private void format(){try{input.setValue(ValueDocument.format(input.getValue()));status="已格式化，可撤销；尚未保存";}catch(Exception error){status=error.getMessage();}}
    private void save(){if(!preview.isEmpty()||save==null)return;try{if(json)ValueDocument.parse(input.getValue());save.accept(input.getValue());minecraft.setScreen(parent);}catch(Exception error){status="错误："+error.getMessage();}}
    @Override public void tick(){if(json&&preview.isEmpty()&&!checked.equals(document.text())){checked=document.text();try{ValueDocument.parse(checked);syntax="JSON 格式有效";}catch(Exception error){syntax=error.getMessage();}}}
    @Override public boolean charTyped(char value,int modifiers){return !preview.isEmpty()||save==null||super.charTyped(value,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!preview.isEmpty()||save==null){if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT||key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_HOME||key==GLFW.GLFW_KEY_END||key==GLFW.GLFW_KEY_PAGE_UP||key==GLFW.GLFW_KEY_PAGE_DOWN||hasControlDown()&&(key==GLFW.GLFW_KEY_C||key==GLFW.GLFW_KEY_A))return super.keyPressed(key,scan,modifiers);return true;}
        if(hasControlDown()){
            if(key==GLFW.GLFW_KEY_S){save();return true;}
            if(key==GLFW.GLFW_KEY_F&&hasShiftDown()){format();return true;}
            if(key==GLFW.GLFW_KEY_Z){input.setValue(hasShiftDown()?document.redo():document.undo());return true;}
            if(key==GLFW.GLFW_KEY_Y){input.setValue(document.redo());return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){
        g.fill(0,0,width,height,0xFF101923);g.drawString(font,font.plainSubstrByWidth(title.getString(),width-32),16,12,0xFF7DD3FC,false);
        String message=!preview.isEmpty()?preview+" · 只读预览；切回原始 JSON 可继续编辑":json&&!syntax.equals("JSON 格式有效")&&!syntax.isEmpty()?syntax:status;
        g.drawString(font,font.plainSubstrByWidth(message,width-32),16,height-50,0xFFFFCC88,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        super.render(g,x,y,partial);
        if(y>=height-54&&y<height-32)g.renderTooltip(font,font.split(Component.literal(syntax+"\n"+status),Math.max(80,width-40)),x,y);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
