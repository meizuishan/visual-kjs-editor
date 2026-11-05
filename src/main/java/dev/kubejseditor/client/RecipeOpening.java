package dev.kubejseditor.client;

import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.WorkspaceState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;

/** Opening is direct; authoring mode is an explicit editor action. */
public final class RecipeOpening {
    public static void choose(Screen parent,String id){WorkspaceScreen.openRecipe(id,parent);}
    private static boolean exists(EditorProject.RecipeDraft original,String id){return EditorRuntime.project().recipes.containsKey(id)||EditorRuntime.state().applied.recipes.containsKey(id)||(original.registryTarget==null?Minecraft.getInstance().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent():RegistryDataSources.read(id)!=null);}
    public static void toggle(Screen parent,EditorProject.RecipeDraft current,Consumer<EditorProject.RecipeDraft> accepted){
        EditorRuntime.requireEditable();
        if(dev.kubejseditor.recipe.RecipeModes.creates(current)&&current.mode==null){
            Minecraft.getInstance().setScreen(new CompatValueScreen(parent,"要替换的配方 ID",current.registryTarget==null?current.id:current.registryTarget.entry,false,text->{
                var loaded=EditorRuntime.recipe(current.registryTarget==null?text.trim():current.copy(text.trim()).id);if(loaded==null||loaded.data()==null)throw new IllegalArgumentException("未找到可替换的来源配方");
                var prepared=WorkspaceState.copy(current,EditorProject.RecipeDraft.class);prepared.mode=new EditorProject.RecipeModeState();prepared.mode.replaceId=loaded.id();prepared.mode.newId=current.id;var prior=EditorRuntime.state().applied.recipes.get(loaded.id());prepared.mode.origin=prior==null?"existing":prior.origin!=null?prior.origin:prior.operation.equals("add")?"add":"existing";prepared.mode.source=prior==null?loaded.data().deepCopy():prior.source==null?null:prior.source.deepCopy();
                var next=dev.kubejseditor.recipe.RecipeModes.toggle(prepared,id->exists(current,id));movePending(current,next);Minecraft.getInstance().tell(()->accepted.accept(next));
            }));return;
        }
        var next=dev.kubejseditor.recipe.RecipeModes.toggle(current,id->exists(current,id));CompatJeiLayouts.copySource(current.id,next.id);movePending(current,next);accepted.accept(next);
    }
    private static void movePending(EditorProject.RecipeDraft current,EditorProject.RecipeDraft next){
        var project=EditorRuntime.project();var old=project.recipes.get(current.id);if(project.recipes.containsKey(next.id)&&!next.id.equals(current.id))throw new IllegalArgumentException("目标 ID 已有其他待办，未切换模式");
        if(old==null)return;if(!dev.kubejseditor.storage.ProjectStore.JSON.toJsonTree(old).equals(dev.kubejseditor.storage.ProjectStore.JSON.toJsonTree(current)))throw new IllegalArgumentException("当前待办已变化，请重新打开后切换");
        project.recipes.remove(current.id);project.recipes.put(next.id,WorkspaceState.copy(next,EditorProject.RecipeDraft.class));try{EditorRuntime.saveDraft();}catch(Exception error){project.recipes.remove(next.id);project.recipes.put(current.id,old);throw new IllegalStateException("切换未保存，原待办保留",error);}
    }
    public static void copy(Screen parent,String sourceId){var loaded=EditorRuntime.recipe(sourceId);if(loaded==null||loaded.data()==null)throw new IllegalArgumentException("没有可复制的真实配方数据");var original=new EditorProject.RecipeDraft(sourceId,loaded.data());original.registryTarget=loaded.registryTarget();copy(parent,original,d->Minecraft.getInstance().setScreen(new CompatibilityScreen(parent,d)));}
    private static void copy(Screen parent,EditorProject.RecipeDraft original,Consumer<EditorProject.RecipeDraft> accepted){
        var mc=Minecraft.getInstance();String base=original.registryTarget==null?original.id:original.registryTarget.entry;
        mc.setScreen(new CompatValueScreen(parent,"新配方 ID · 原配方保留","kubejs_editor:copy/"+base.replace(':','/'),false,text->{
            EditorRuntime.requireEditable();String id=text.trim();EditorProject.requireId(id);var copy=original.copy(id);copy.operation="add";copy.origin="add";copy.source=null;copy.forceNative=true;
            boolean exists=EditorRuntime.project().recipes.containsKey(copy.id)||EditorRuntime.state().applied.recipes.containsKey(copy.id)||(copy.registryTarget==null?mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(copy.id)).isPresent():RegistryDataSources.read(copy.registryTarget)!=null);
            if(exists)throw new IllegalArgumentException("ID 已存在，请选择新的 ID");CompatJeiLayouts.copySource(original.id,copy.id);mc.tell(()->accepted.accept(copy));
        }));
    }
    private RecipeOpening(){}
}
