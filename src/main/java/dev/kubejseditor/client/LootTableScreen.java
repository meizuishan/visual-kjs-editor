package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class LootTableScreen extends Screen {
    private final Screen parent;public final EditorProject.RecipeDraft draft;private String query="",status="修改共享此掉落表的所有使用者；击杀条件、抢夺和其他函数保留。";private int offset;
    public LootTableScreen(Screen parent,RegistryEntryTarget target){super(Component.literal("掉落表 · "+target.entry));this.parent=parent;
        var source=EditorRuntime.recipe(target.id());if(source==null||source.data()==null)throw new IllegalArgumentException(source==null?"掉落表不存在":source.error());
        var saved=EditorRuntime.project().recipes.getOrDefault(target.id(),EditorRuntime.state().applied.recipes.get(target.id()));
        draft=saved==null?new EditorProject.RecipeDraft(target.id(),source.data()):WorkspaceState.copy(saved,EditorProject.RecipeDraft.class);
        if(saved==null){draft.source=source.data().deepCopy();draft.registryTarget=source.registryTarget();draft.operation="replace";draft.origin="existing";}draft.forceNative=true;
    }
    @Override protected void init(){
        var search=addRenderableWidget(new EditBox(font,16,34,width-32,20,Component.literal("搜索物品或字段")));search.setValue(query);search.setResponder(text->{query=text;offset=0;rebuildWidgets();});if(!query.isEmpty())setInitialFocus(search);
        button("新增掉落",16,60,90,()->edit(null));button("编辑 JSON",110,60,90,()->minecraft.setScreen(new CompatValueScreen(this,"完整掉落表 JSON",ProjectStore.JSON.toJson(draft.data),text->stage(dev.kubejseditor.script.ValueDocument.parse(text).getAsJsonObject()))));
        button("字段补丁",204,60,90,()->minecraft.setScreen(new FieldPatchScreen(this,draft.data,this::stage)));
        var entries=LootEntries.items(draft.data).stream().filter(e->(e.item()+e.path()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();int rows=Math.max(1,(height-160)/24);offset=Math.min(offset,Math.max(0,entries.size()-rows));
        for(int i=offset;i<Math.min(entries.size(),offset+rows);i++){var e=entries.get(i);int y=90+(i-offset)*24;button(font.plainSubstrByWidth(e.item()+" ×"+e.minimum()+"～"+e.maximum(),width-100),16,y,width-80,()->edit(e));button("×",width-56,y,40,()->stage(LootEntries.remove(draft.data,e.path())));}
        button("一键应用",16,height-30,100,this::apply);button("导出数据包",120,height-30,110,()->minecraft.setScreen(new DatapackExportScreen(this,draft)));button("返回",width-96,height-30,80,this::onClose);
    }
    private void button(String text,int x,int y,int w,Runnable run){addRenderableWidget(Button.builder(Component.literal(text),b->{try{run.run();}catch(Exception error){status="错误："+error.getMessage();}}).bounds(x,y,w,20).build());}
    public void stage(JsonObject next){EditorRuntime.requireEditable();EditorRuntime.validatePatch(draft,next);var copy=WorkspaceState.copy(draft,EditorProject.RecipeDraft.class);copy.data=next.deepCopy();var previous=EditorRuntime.project().recipes.put(draft.id,copy);try{EditorRuntime.saveDraft();}catch(Exception error){if(previous==null)EditorRuntime.project().recipes.remove(draft.id);else EditorRuntime.project().recipes.put(draft.id,previous);throw new IllegalStateException(error);}draft.data=next.deepCopy();status="已加入待办；一键应用后重载并核对掉落表";}
    private void edit(LootEntries.Entry entry){minecraft.setScreen(new LootEntryScreen(this,draft.data,entry,this::stage));}
    private void apply(){try{var review=EditorRuntime.review();EditorRuntime.apply(review);if(review.restartRequired()){status="同批包含启动期修改，请重启生效";return;}EditorRuntime.reloadResult(review.resourcesChanged(),result->{status=result.message();rebuildWidgets();});status="正在重载并核对掉落表";}catch(Exception error){status="错误："+error.getMessage();}}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawString(font,font.plainSubstrByWidth(title.getString(),width-32),16,12,0xFF7DD3FC,false);super.render(g,x,y,partial);g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-48,0xFFFFCC88,false);if(y>=height-52&&y<height-32)g.renderTooltip(font,font.split(Component.literal(status),width-40),x,y);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){offset=Math.max(0,offset-(int)dy*3);rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
