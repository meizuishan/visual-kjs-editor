package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Real serializers, GUI inputs, disk staging, apply/reload and revoke in a fresh fixture world. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientLearningVerification {
    private static int phase,ticks,checks;
    private static final JsonArray notes=new JsonArray();
    private static EditorRuntime.ReloadResult result;
    private static CompatibilityScreen editor;
    private static JsonObject source;
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static Path root(){return mc().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;notes.add(message);if(!ok)throw new AssertionError(message);}
    private static void click(String text){var screen=mc().screen;var b=screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals(text)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing button: "+text));check(b.active,"Enabled button: "+text);b.onPress();}
    private static void number(String path,String value){var input=mc().screen.children().stream().filter(c->c instanceof EditBox box&&box.getMessage().getString().equals(path)).map(c->(EditBox)c).findFirst().orElseThrow(()->new AssertionError("Missing numeric input: "+path));input.setValue(value);}
    private static MultiLineEditBox text(){return mc().screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();}
    private static void geometry(){var screen=mc().screen;for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w)check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=screen.width&&w.getY()+w.getHeight()<=screen.height,"Control fits GUI: "+w.getMessage().getString());}
    private static void start()throws Exception{
        EditorRuntime.project().recipes.clear();EditorRuntime.saveDraft();
        realMods();
        var loaded=EditorRuntime.recipe("learning:source");check(loaded!=null&&loaded.data()!=null,"Generic resource is editable through the ordinary recipe source path");source=loaded.data().deepCopy();
        check(source.has("extensions")&&source.getAsJsonObject("extensions").get("keep").getAsString().equals("original"),"Import retains fields absent from the native encoder");
        var stale=EditorRuntime.recipe("learning:stale");check(JsonPatches.read(stale.data(),"/settings/work").getAsInt()==22&&!stale.data().has("extensions"),"Live KubeJS changes override stale resource JSON");
        var empty=new CompatDefinition(source.get("type").getAsString(),"confirmed");CompatibilityRecipes.confirm(empty,source,true);
        CompatibilityScreenOpen.open(new WorkspaceScreen(),"learning:source");check(mc().screen instanceof CompatibilityScreen,"Ordinary source opens in the shared compatibility editor");editor=(CompatibilityScreen)mc().screen;
        editor.openNumbers();check(mc().screen instanceof NumericParametersScreen,"Numbers open even with a deliberately empty slot map");geometry();
        number("/settings/work","55");click("取消");check(editor.draft.data.equals(source),"Canceling numeric input leaves the parent draft unchanged");
        editor.openNumbers();number("/settings/work","4294967367");number("/settings/energy","7777");click("检查并保存");
        check(mc().screen instanceof NumericParametersScreen&&editor.draft.data.equals(source),"Integer overflow fails atomically despite a second valid numeric edit");
        number("/settings/work","77");number("/settings/energy","9007199254740993123456789");click("检查并保存");
        check(mc().screen==editor&&JsonPatches.read(editor.draft.data,"/settings/work").getAsInt()==77,"Valid numeric editing returns to the parent draft");
        check(JsonPatches.read(editor.draft.data,"/settings/energy").getAsJsonPrimitive().isString(),"Native numeric strings keep their JSON string type");
        check(JsonPatches.read(editor.draft.data,"/settings/energy").getAsString().equals("9007199254740993123456789"),"Large numeric strings preserve every digit");
        check(editor.draft.data.has("extensions"),"Unrelated source metadata survives numeric editing");
        check(CompatibilityRecipes.policy(source.get("type").getAsString(),editor.draft.data).fields.isEmpty(),"Numeric editing preserves the confirmed empty mapping policy");
        check(JsonPatches.read(ProjectStore.read(root()).recipes.get("learning:source").data,"/settings/work").getAsInt()==77,"Numeric changes persist to the normal draft file");
        String clipboard=mc().keyboardHandler.getClipboard();String[] saved={null};
        try{
            var screen=new CompatValueScreen(editor,"JSON 功能验证","{\"n\":9007199254740993123456789,\"empty\":null}",value->saved[0]=value);mc().setScreen(screen);geometry();
            String initial=text().getValue();click("格式化");String formatted=text().getValue();check(!formatted.equals(initial)&&formatted.contains("null"),"JSON formatting preserves explicit nulls");
            click("撤销");check(text().getValue().equals(initial),"GUI undo restores the exact unformatted draft");click("重做");check(text().getValue().equals(formatted),"GUI redo restores the formatted draft");
            text().setValue("{\n\"n\":");click("复制全文");check(mc().keyboardHandler.getClipboard().equals("{\n\"n\":"),"Copy includes unfinished JSON without modifying it");
            click("检查并保存");check(mc().screen==screen&&saved[0]==null,"Malformed JSON cannot reach the save callback");
            text().setValue(formatted);mc().options.guiScale().set(3);mc().resizeDisplay();geometry();check(text().getValue().equals(formatted),"Changing GUI scale preserves unsaved JSON text");
            click("检查并保存");check(mc().screen==editor&&saved[0].equals(formatted),"Valid JSON saves through the original callback");
            String[] plain={null};mc().setScreen(new CompatValueScreen(editor,"原始文字","{literal text",false,value->plain[0]=value));click("检查并保存");check(plain[0].equals("{literal text"),"Plain string fields are not forced into JSON syntax");
            mc().options.guiScale().set(2);mc().resizeDisplay();
        }finally{mc().keyboardHandler.setClipboard(clipboard);}
        editor.openNumbers();number("/settings/work","88");mc().options.guiScale().set(3);mc().resizeDisplay();geometry();mc().options.guiScale().set(2);mc().resizeDisplay();
        check(mc().screen.children().stream().anyMatch(c->c instanceof EditBox box&&box.getMessage().getString().equals("/settings/work")&&box.getValue().equals("88")),"Numeric input survives GUI resizing");click("取消");
        check(JsonPatches.read(editor.draft.data,"/settings/work").getAsInt()==77,"Cancel after resizing still discards only the local numeric edits");
        var review=EditorRuntime.review();check(!review.restartRequired(),"Ordinary numeric changes retain the standard reload path");EditorRuntime.apply(review);result=null;phase=2;EditorRuntime.reloadResult(false,r->result=r);
    }
    private static void realMods(){
        var server=mc().getSingleplayerServer();
        for(String namespace:List.of("actuallyadditions","powah","create")){
            if(!net.neoforged.fml.ModList.get().isLoaded(namespace))continue;
            var holders=server.getRecipeManager().getRecipes().stream().filter(h->h.id().getNamespace().equals(namespace)).sorted(Comparator.comparing(h->h.id().toString())).toList();
            int checked=0;
            for(var holder:holders){
                var loaded=EditorRuntime.recipe(holder.id().toString());if(loaded==null||loaded.data()==null||!loaded.type().startsWith(namespace+":"))continue;
                var fields=NumericParameters.fields(loaded.data());
                for(var field:fields){
                    if(!field.path().toLowerCase(Locale.ROOT).matches(".*(?:energy|time|duration|experience|work).*")||field.value().equals("0"))continue;
                    var draft=new dev.kubejseditor.model.EditorProject.RecipeDraft();draft.id=loaded.id();draft.data=loaded.data().deepCopy();draft.operation="replace";draft.forceNative=true;
                    var number=new java.math.BigDecimal(field.value()).multiply(new java.math.BigDecimal("2"));
                    try{var next=NumericParameters.apply(draft.data,Map.of(field.path(),number.toString()));EditorRuntime.validatePatch(draft,next);check(!next.equals(draft.data),"Real native numeric edit validated: "+loaded.id()+" "+field.path());checked++;break;}catch(RuntimeException rejected){/* Pick a field whose actual native range permits a change. */}
                }
                if(checked>=3)break;
            }
            check(checked>=3,"Three real recipes validate through the generic numeric route: "+namespace);
        }
    }
    private static void applied()throws Exception{
        check(result.success(),"Numeric draft applies and reloads successfully: "+result.message());
        var actual=mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse("learning:source")).orElseThrow();
        var recipe=(GenericCompatFixture.GenericRecipe)actual.value();check(recipe.settings().work()==77&&recipe.settings().energy().equals("9007199254740993123456789"),"Real server recipe contains the edited integer and exact string");
        check(EditorRuntime.state().applied.recipes.get("learning:source").data.has("extensions"),"Applied rule retains complete original JSON in managed state");
        result=null;phase=3;EditorRuntime.revokeRecipe("learning:source",r->result=r);
    }
    private static void finish()throws Exception{
        check(result.success(),"Normal revoke succeeds after numeric editing");
        var restored=EditorRuntime.recipe("learning:source");check(JsonPatches.read(restored.data(),"/settings/work").getAsInt()==7&&restored.data().has("extensions"),"Revoke restores both native data and original resource extensions");
        check(!EditorRuntime.state().applied.recipes.containsKey("learning:source"),"Revoked rule leaves the managed state");
        write(null);phase=99;mc().stop();
    }
    private static void write(Throwable error)throws Exception{var report=new JsonObject();report.addProperty("passed",error==null);report.addProperty("checks",checks);report.addProperty("screenshots",false);report.add("details",notes);if(error!=null){var trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));report.addProperty("error",trace.toString());}Files.writeString(root().resolve("compat-learning.json"),ProjectStore.JSON.toJson(report));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyLearning")||phase==99)return;ticks++;
        try{
            if(ticks>10000)throw new AssertionError("Learning verification timed out at "+phase);
            if(phase==0&&mc().screen instanceof TitleScreen&&ticks>80){mc().createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;}
            else if(phase==1&&mc().level!=null&&mc().player!=null&&WorkspaceJeiPlugin.runtime!=null&&ticks>180)start();
            else if(phase==2&&result!=null&&!EditorRuntime.busy())applied();
            else if(phase==3&&result!=null&&!EditorRuntime.busy())finish();
        }catch(Throwable error){error.printStackTrace();try{write(error);}catch(Exception write){write.printStackTrace();}phase=99;mc().stop();}
    }
    private ClientLearningVerification(){}
}
