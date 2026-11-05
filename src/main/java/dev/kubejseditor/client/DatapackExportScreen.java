package dev.kubejseditor.client;

import dev.kubejseditor.runtime.*;
import dev.kubejseditor.script.NativeDatapack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Path;
import java.util.*;

/** Export is an explicit snapshot; it never changes the current editing workspace. */
public final class DatapackExportScreen extends Screen {
    private final Screen parent;private final dev.kubejseditor.model.EditorProject.RecipeDraft current;private NativeDatapack.Plan plan;private String status="导出不会自动应用或替换当前规则。";
    public DatapackExportScreen(Screen parent){this(parent,null);}
    public DatapackExportScreen(Screen parent,dev.kubejseditor.model.EditorProject.RecipeDraft current){super(Component.literal("导出原生数据包"));this.parent=parent;this.current=current;}
    @Override protected void init(){
        try{var content=EditorRuntime.content();if(current!=null)content.recipes.put(current.id,dev.kubejseditor.storage.WorkspaceState.copy(current,dev.kubejseditor.model.EditorProject.RecipeDraft.class));for(var draft:content.recipes.values())if(draft.enabled&&(draft.registryTarget!=null||!draft.operation.equals("remove")))EditorRuntime.validateRecipe(draft,Set.of());plan=NativeDatapack.plan(content);}catch(Exception error){status="无法导出："+error.getMessage();plan=null;}
        int w=Math.min(340,width-32),x=(width-w)/2;
        var local=addRenderableWidget(Button.builder(Component.literal("导出到本机文件夹"),b->export(false)).bounds(x,height-82,w,20).build());local.active=plan!=null;
        var world=addRenderableWidget(Button.builder(Component.literal("导出到当前世界数据包目录"),b->export(true)).bounds(x,height-58,w,20).build());world.active=plan!=null&&EditorRuntime.canApply();
        addRenderableWidget(Button.builder(Component.literal("返回"),b->onClose()).bounds(x,height-30,w,20).build());
    }
    public Path export(boolean world){try{if(plan==null)throw new IllegalStateException("没有有效的导出内容");
        String name="kubejs_editor_"+System.currentTimeMillis()+"_"+UUID.randomUUID().toString().substring(0,8);
        var parentPath=world?minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve("datapacks"):EditorRuntime.root().resolve("kubejs_editor_exports");
        var result=NativeDatapack.write(parentPath.resolve(name),plan);status="已导出："+result+"；启用数据包后按数据类型重载或重启";return result;
    }catch(Exception error){status="导出失败："+error.getMessage();return null;}}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawCenteredString(font,title,width/2,14,0xFF7DD3FC);
        String text=plan==null?status:"包含 "+plan.recipes()+" 条配方和 "+plan.dataEntries()+" 条原生数据，来自当前待办及已有修改。\n\n"+String.join("\n\n",plan.warnings());g.enableScissor(12,38,width-12,height-108);g.drawWordWrap(font,Component.literal(text),16,42,width-32,0xFFCBD5E1);g.disableScissor();
        g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-102,0xFFFFCC88,false);super.render(g,x,y,partial);if(y>=height-106&&y<height-84)g.renderTooltip(font,font.split(Component.literal(status),width-40),x,y);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public boolean isPauseScreen(){return false;}
}
