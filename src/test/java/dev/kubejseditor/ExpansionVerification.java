package dev.kubejseditor;

import com.google.gson.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.script.*;
import java.nio.file.*;
import java.util.*;

public final class ExpansionVerification {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void fails(Throwing call,String message)throws Exception{checks++;try{call.run();}catch(Exception expected){return;}throw new AssertionError(message);}
    @FunctionalInterface private interface Throwing{void run()throws Exception;}
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    public static int run()throws Exception{
        var table=json("{type:'minecraft:entity',random_sequence:'example:seed',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'minecraft:iron_ingot',weight:4,conditions:[{condition:'minecraft:killed_by_player'},{condition:'minecraft:random_chance',chance:0.5}],functions:[{function:'minecraft:set_count',count:2,add:true,conditions:[{condition:'minecraft:killed_by_player'}]},{function:'example:opaque',payload:17}]}]}],extension:{keep:true}}");
        var baseline=table.deepCopy();var entries=LootEntries.items(table);check(entries.size()==1&&entries.getFirst().minimum().equals("2"),"Existing item leaves retain their full source paths and quantity");
        var edited=LootEntries.edit(table,entries.getFirst().path(),"minecraft:gold_ingot","3","5","0.25",null);
        var leaf=JsonPatches.read(edited,entries.getFirst().path()).getAsJsonObject();
        check(table.equals(baseline),"Loot previews never mutate their original table");check(leaf.get("weight").getAsInt()==4&&edited.has("random_sequence")&&edited.has("extension"),"Unknown fields, weights and sequence context survive edits");
        check(leaf.getAsJsonArray("conditions").size()==2&&leaf.getAsJsonArray("conditions").get(0).getAsJsonObject().get("condition").getAsString().equals("minecraft:killed_by_player"),"Changing chance preserves the kill predicate");
        check(leaf.getAsJsonArray("functions").get(0).getAsJsonObject().get("add").getAsBoolean()&&leaf.getAsJsonArray("functions").get(0).getAsJsonObject().has("conditions"),"Quantity edits retain conditional and additive function semantics");
        check(leaf.getAsJsonArray("functions").get(1).getAsJsonObject().get("function").getAsString().equals("example:opaque"),"Custom loot functions retain their order and data");
        var components=json("{'minecraft:custom_data':{sample:'kept'},'minecraft:custom_name':'sample'}");var added=LootEntries.edit(edited,null,"minecraft:diamond","1","1","1",components);
        check(added.getAsJsonArray("pools").size()==2&&LootEntries.items(added).size()==2,"Adding an independent drop pool leaves old entries intact");
        var addedEntry=LootEntries.items(added).getLast();var item=JsonPatches.read(added,addedEntry.path()).getAsJsonObject();check(item.toString().contains("minecraft:set_components")&&item.toString().contains("kept"),"Sample components are emitted through the native component function");
        var removed=LootEntries.remove(added,entries.getFirst().path());check(LootEntries.items(removed).size()==1&&LootEntries.items(removed).getFirst().item().equals("minecraft:diamond"),"Removing one item does not remove sibling pools");
        check(LootEntries.edit(table,entries.getFirst().path(),"minecraft:iron_ingot","","","",null).equals(table),"Empty optional edits preserve complex original structure");
        fails(()->LootEntries.edit(table,entries.getFirst().path(),"minecraft:stone","4","2","1",null),"Inverted quantity ranges are rejected");
        fails(()->LootEntries.edit(table,entries.getFirst().path(),"minecraft:stone","1","2","1.1",null),"Invalid probabilities are rejected");
        fails(()->LootEntries.edit(table,entries.getFirst().path(),"minecraft:stone","1e100000000","1e100000000","1",null),"Out-of-range quantities fail before integer expansion");
        var multiple=table.deepCopy();multiple.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("functions").add(json("{function:'minecraft:set_count',count:1}"));
        fails(()->LootEntries.edit(multiple,entries.getFirst().path(),"minecraft:stone","1","2","1",null),"Multiple count functions require explicit JSON editing instead of flattening their order");

        var tree=json("{type:'example:machine',inputs:[{ingredient:{item:'minecraft:iron_ingot'},count:8}],settings:{energy:'9007199254740993',enabled:true},'a/b~c':{value:42}}");
        check(FieldTree.nodes(tree,"","").stream().noneMatch(n->n.path().equals("/type")),"The native dispatch type is not offered as an editable mapping");
        check(FieldTree.nodes(tree,"/inputs","").getFirst().path().equals("/inputs/0"),"Tree navigation preserves list indices");
        check(FieldTree.nodes(tree,"","iron_ingot").stream().anyMatch(n->n.path().equals("/inputs/0/ingredient/item")),"Search locates exact paths by their current value");
        check(FieldTree.nodes(tree,"/settings","数值").stream().anyMatch(n->n.path().equals("/settings/energy")),"Numeric strings have useful search hints");
        check(FieldTree.nodes(tree,"","").stream().anyMatch(n->n.path().equals("/a~1b~0c")),"Tree pointers preserve slash and tilde escapes");
        check(FieldTree.parent("/inputs/0/ingredient").equals("/inputs/0")&&FieldTree.parent("/inputs").isEmpty(),"Parent navigation works at root and nested levels");

        var project=new EditorProject();var recipe=new EditorProject.RecipeDraft("example:smelt",json("{type:'minecraft:smelting',ingredient:{item:'minecraft:stone'},result:{id:'minecraft:diamond'},cookingtime:200}"));recipe.forceNative=true;project.recipes.put(recipe.id,recipe);
        var disable=new EditorProject.RecipeDraft();disable.id="example:disabled";disable.operation="remove";project.recipes.put(disable.id,disable);
        var loot=new EditorProject.RecipeDraft();loot.registryTarget=new RegistryEntryTarget("minecraft:loot_table","example:entities/test");loot.registryTarget.codecSource="reloadable";loot.data=loot.registryTarget.document(added);loot.id=loot.registryTarget.id();loot.operation="replace";project.recipes.put(loot.id,loot);
        var array=new EditorProject.RecipeDraft();array.registryTarget=new RegistryEntryTarget("minecraft:item_modifier","example:sample");array.registryTarget.codecSource="reloadable";array.data=array.registryTarget.document(JsonParser.parseString("[{function:'minecraft:set_count',count:3}]"));array.id=array.registryTarget.id();project.recipes.put(array.id,array);
        check(array.registryTarget.valueEnvelope&&array.data.has("type")&&array.registryTarget.nativeBody(array.data).isJsonArray(),"Non-object native roots have an editor envelope without changing their native representation");
        var conditionalArray=dev.kubejseditor.storage.WorkspaceState.copy(array,EditorProject.RecipeDraft.class);conditionalArray.data.add(LoadConditions.KEY,JsonParser.parseString("[{type:'neoforge:false'}]"));
        fails(()->conditionalArray.registryTarget.generatedValue(conditionalArray),"Array export refuses object loading conditions instead of silently discarding them");
        fails(()->conditionalArray.registryTarget.generated(conditionalArray),"Runtime condition evaluation shares the native array format guard");
        conditionalArray.loadConditionPolicy=LoadConditions.IGNORE;check(conditionalArray.registryTarget.generatedValue(conditionalArray).isJsonArray(),"Explicitly ignoring loading metadata retains the original native array");
        var plan=NativeDatapack.plan(project);check(plan.recipes()==2&&plan.dataEntries()==2,"Native export includes ordinary recipes and reloadable data together");
        check(plan.files().containsKey("data/example/loot_table/entities/test.json")&&plan.files().containsKey("data/example/item_modifier/sample.json"),"Native destination directories use Minecraft 1.21 singular names");
        var nativeArray=JsonParser.parseString(new String(plan.files().get("data/example/item_modifier/sample.json"),java.nio.charset.StandardCharsets.UTF_8));check(nativeArray.isJsonArray(),"Native arrays export without editor type/value envelopes");
        var disabled=JsonParser.parseString(new String(plan.files().get("data/example/recipe/disabled.json"),java.nio.charset.StandardCharsets.UTF_8));check(disabled.getAsJsonObject().getAsJsonArray(LoadConditions.KEY).get(0).getAsJsonObject().get("type").getAsString().equals("neoforge:false"),"Recipe disable exports as a native false condition");
        check(plan.files().keySet().stream().noneMatch(p->p.startsWith("kubejs/")||p.endsWith(".js")),"Standalone packs contain neither scripts nor editor state");
        check(!KjsGenerator.generate(project).restartRequired(),"Reloadable-only native data does not acquire a startup requirement");
        var staticTarget=new RegistryEntryTarget("example:ritual","example:test");var staticDraft=new EditorProject.RecipeDraft(staticTarget.id(),json("{type:'example:ritual',cost:2}"));staticDraft.registryTarget=staticTarget;project.recipes.put(staticDraft.id,staticDraft);check(KjsGenerator.generate(project).restartRequired(),"Declared startup registries keep their existing restart requirement");
        var folder=Path.of("build","native-export-tests",UUID.randomUUID().toString()).toAbsolutePath();NativeDatapack.write(folder,plan);check(Files.exists(folder.resolve("pack.mcmeta"))&&Files.exists(folder.resolve("data/example/loot_table/entities/test.json")),"Standalone export writes a complete pack");
        fails(()->NativeDatapack.write(folder,plan),"Export refuses an existing pack directory");
        var invalid=new NativeDatapack.Plan(Map.of("../escape.txt",new byte[]{1},"pack.mcmeta",new byte[]{2}),1,0,List.of());fails(()->NativeDatapack.write(folder.resolveSibling("invalid-"+UUID.randomUUID()),invalid),"Export rejects path traversal");
        var precision=dev.kubejseditor.storage.WorkspaceState.copy(recipe,EditorProject.RecipeDraft.class);precision.data.addProperty("opaque_large_integer",new java.math.BigInteger("9223372036854775807"));var rawProject=new EditorProject();rawProject.recipes.put(precision.id,precision);var code=RecipeDocumentViews.code(precision,dev.kubejseditor.storage.ProjectStore.JSON.toJson(precision.data));
        check(!code.contains("project.json")&&!code.contains("schemaVersion"),"Single recipe previews exclude workspace and rollback metadata");
        var marker="editorNative.add(event,"+KjsGenerator.quote(precision.id)+",";int at=code.indexOf(marker);check(at>=0&&!code.contains("event.custom("),"Forced recipes use the native JSON carrier rather than KubeJS field schemas");
        String literal=code.substring(at+marker.length(),code.indexOf(");",at));var decoded=JsonParser.parseString(JsonParser.parseString(literal).getAsString()).getAsJsonObject();check(decoded.equals(precision.data)&&decoded.get("opaque_large_integer").getAsBigInteger().equals(new java.math.BigInteger("9223372036854775807")),"Native JSON payloads retain large integers and exact unknown data without a JavaScript numeric conversion");
        check(precision.data.has("opaque_large_integer")&&precision.source==recipe.source,"Preview generation does not stage or mutate recipe provenance");
        var modeDraft=new EditorProject.RecipeDraft("example:base",json("{type:'example:native',work:7,unknown:{kept:true}}"));modeDraft.operation="replace";modeDraft.origin="existing";modeDraft.source=modeDraft.data.deepCopy();modeDraft.forceNative=true;
        var known=new HashSet<String>(Set.of(modeDraft.id,"kubejs_editor:copy/example/base"));var modeAdded=RecipeModes.toggle(modeDraft,known::contains);check(modeAdded.operation.equals("add")&&modeAdded.source==null&&modeAdded.id.endsWith("_2"),"Mode switch allocates a collision-free new ID without a replacement snapshot");
        check(modeAdded.data.equals(modeDraft.data)&&modeDraft.mode==null&&modeDraft.id.equals("example:base"),"Mode switch retains native content and leaves the previous draft intact");modeAdded.data.addProperty("work",13);var back=RecipeModes.toggle(modeAdded,known::contains);check(back.id.equals(modeDraft.id)&&back.operation.equals("replace")&&back.data.get("work").getAsInt()==13&&back.source.equals(modeDraft.source),"Switch back restores the source ID and rollback snapshot while retaining current edits");
        var reopened=dev.kubejseditor.storage.ProjectStore.JSON.fromJson(dev.kubejseditor.storage.ProjectStore.JSON.toJson(modeAdded),EditorProject.RecipeDraft.class);check(RecipeModes.toggle(reopened,known::contains).id.equals(modeDraft.id),"Mode provenance survives schema-2 serialization");check(RecipeModes.toggle(back,known::contains).id.equals(modeAdded.id),"Repeated switches reuse the unoccupied generated ID");
        var owned=dev.kubejseditor.storage.WorkspaceState.copy(modeAdded,EditorProject.RecipeDraft.class);owned.operation="replace";var activeIds=new HashSet<>(known);activeIds.add(owned.id);var independent=RecipeModes.toggle(owned,activeIds::contains);check(independent.mode.replaceId.equals(owned.id)&&RecipeModes.toggle(independent,activeIds::contains).id.equals(owned.id),"Reopening an applied new recipe treats its own ID as the replacement source");
        fails(()->RecipeModes.toggle(modeAdded,id->false),"A vanished replacement source cannot be silently recreated");
        check(!RecipeDocumentViews.code(modeAdded,modeAdded.data.toString()).contains("replaceId"),"Mode metadata is absent from generated recipe code");
        return checks;
    }
    private ExpansionVerification(){}
}
