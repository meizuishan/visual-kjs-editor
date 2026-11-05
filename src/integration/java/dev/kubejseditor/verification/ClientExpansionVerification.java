package dev.kubejseditor.verification;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.kubejseditor.client.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.script.NativeDatapack;
import dev.kubejseditor.storage.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** One isolated world exercises UI sampling, native loot application and an independent exported pack. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientExpansionVerification {
    private static final String LOOT="expansion:entities/test",MODIFIER="expansion:sample";
    private static final RegistryEntryTarget TARGET=new RegistryEntryTarget("minecraft:loot_table",LOOT);
    private static int phase,ticks,checks;private static final JsonArray notes=new JsonArray();
    private static EditorRuntime.ReloadResult result;private static CompletableFuture<Void> externalReload;private static Path exported;private static String packId;private static JsonObject originalLoot;
    private static Minecraft mc(){return Minecraft.getInstance();}private static Path root(){return mc().gameDirectory.toPath();}
    private static void check(boolean ok,String note){checks++;notes.add(note);if(!ok)throw new AssertionError(note);}
    private static Object field(Object value,String key)throws Exception{var f=value.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(value);}
    private static void click(String label){var screen=mc().screen;var b=screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing button "+label));check(b.active,"Enabled button: "+label);b.onPress();}
    private static void clickPath(String path){var b=mc().screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().startsWith(path+" ·")).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing field "+path));b.onPress();}
    private static void text(String label,String value){var box=mc().screen.children().stream().filter(c->c instanceof EditBox b&&b.getMessage().getString().equals(label)).map(c->(EditBox)c).findFirst().orElseThrow(()->new AssertionError("Missing input "+label));box.setValue(value);}
    private static void geometry(){for(var c:mc().screen.children())if(c instanceof AbstractWidget w)check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=mc().screen.width&&w.getY()+w.getHeight()<=mc().screen.height,"Control fits "+mc().screen.getClass().getSimpleName()+": "+w.getMessage().getString());}
    private static void pickSample()throws Exception{check(mc().screen instanceof InventorySampleScreen,"Inventory button opens a copy-only sampling page");var screen=(InventorySampleScreen)mc().screen;int left=(Integer)field(screen,"left"),top=(Integer)field(screen,"top"),cell=(Integer)field(screen,"cell");screen.mouseClicked(left+3,top+cell+3,0);click("使用选中样本");}
    private static JsonElement stack(ItemStack value){return ItemStack.CODEC.encodeStart(mc().level.registryAccess().createSerializationContext(JsonOps.INSTANCE),value).getOrThrow();}
    public record PublicProjection(ResourceLocation id,List<net.minecraft.world.item.crafting.Ingredient> ingredients,int cookingTime,float experience){public List<net.minecraft.world.item.crafting.Ingredient> getIngredients(){return ingredients;}public int getCookingTime(){return cookingTime;}public float getExperience(){return experience;}}
    public record IdProjection(ResourceLocation id){}
    private static void publicIdentity(){
        var manager=mc().getSingleplayerServer().getRecipeManager();var holder=manager.getRecipes().stream().filter(h->h.value() instanceof net.minecraft.world.item.crafting.SmeltingRecipe).findFirst().orElseThrow();var recipe=(net.minecraft.world.item.crafting.SmeltingRecipe)holder.value();var values=List.copyOf(recipe.getIngredients());
        check(!PublicRecipeIdentifiers.verified(new IdProjection(holder.id()),holder.id(),manager),"A public ID alone cannot silently prove a display projection");
        check(PublicRecipeIdentifiers.verified(new PublicProjection(holder.id(),values,recipe.getCookingTime(),recipe.getExperience()),holder.id(),manager),"Public ID and independently matching inputs and numbers prove a projection");
        check(!PublicRecipeIdentifiers.verified(new PublicProjection(holder.id(),values,recipe.getCookingTime()+1,recipe.getExperience()),holder.id(),manager),"A conflicting public numeric field leaves the source unconfirmed");
        check(!PublicRecipeIdentifiers.verified(new PublicProjection(holder.id(),List.of(net.minecraft.world.item.crafting.Ingredient.of(Items.BARRIER)),recipe.getCookingTime(),recipe.getExperience()),holder.id(),manager),"Conflicting ingredients cannot be accepted just because the ID exists");
        check(PublicRecipeIdentifiers.candidates(new IdProjection(ResourceLocation.parse("expansion:missing")),manager).isEmpty(),"A display ID absent from the native manager is never a source");
    }
    private static String copiedId;private static EditorProject.RecipeDraft copiedForApply;
    private static void opening()throws Exception{
        publicIdentity();check(EditorRuntime.project().recipes.isEmpty(),"Opening tests start without pending rules");var parent=new WorkspaceScreen();RecipeOpening.choose(parent,"expansion:generic");check(!(mc().screen instanceof ChoiceScreen),"Opening an existing recipe enters the editor without a mode prompt");
        CompatibilityScreenOpen.open(parent,"expansion:generic");var editor=(CompatibilityScreen)mc().screen;click("模式：替换");check(mc().screen==editor&&editor.draft.operation.equals("add"),"Mode toggle switches directly to new without another screen");check(editor.draft.id.startsWith("kubejs_editor:copy/")&&EditorRuntime.project().recipes.isEmpty(),"New mode automatically assigns a free ID without staging an untouched recipe");
        String generatedId=editor.draft.id;click("模式：新建");check(mc().screen==editor&&editor.draft.id.equals("expansion:generic"),"Toggle back restores the existing ID on the same editor");click("模式：替换");check(editor.draft.id.equals(generatedId),"Repeated toggles retain the generated ID");copiedId=editor.draft.id;phase=10;
    }
    private static void documents()throws Exception{
        check(mc().screen instanceof CompatibilityScreen,"Generate opens an independent compatibility draft");var editor=(CompatibilityScreen)mc().screen;check(editor.draft.id.equals(copiedId)&&editor.draft.operation.equals("add")&&editor.draft.source==null,"Generated draft is additive and has no replacement source");check(EditorRuntime.project().recipes.isEmpty(),"Choosing a new ID alone does not stage a rule");var original=editor.draft.data.deepCopy();editor.openRawJson();
        var document=(CompatValueScreen)mc().screen;var text=document.children().stream().filter(v->v instanceof MultiLineEditBox).map(v->(MultiLineEditBox)v).findFirst().orElseThrow();var changed=original.deepCopy();CompatJson.set(changed,"/settings/work",new JsonPrimitive(91));String unsaved=ProjectStore.JSON.toJson(changed);text.setValue(unsaved);click("生成代码");
        text=mc().screen.children().stream().filter(v->v instanceof MultiLineEditBox).map(v->(MultiLineEditBox)v).findFirst().orElseThrow();check(text.getValue().contains("91"),"Generated code previews the unsaved JSON");check(!text.getValue().contains("project.json")&&!text.getValue().contains("schemaVersion"),"Recipe code omits editor workspace metadata");String code=text.getValue();document.charTyped('X',0);check(text.getValue().equals(code),"Generated code cannot be edited accidentally");click("差异");text=mc().screen.children().stream().filter(v->v instanceof MultiLineEditBox).map(v->(MultiLineEditBox)v).findFirst().orElseThrow();check(text.getValue().contains("91"),"Diff previews the same unsaved document");click("原始 JSON");text=mc().screen.children().stream().filter(v->v instanceof MultiLineEditBox).map(v->(MultiLineEditBox)v).findFirst().orElseThrow();check(text.getValue().equals(unsaved),"Switching tabs restores the exact unsaved JSON");mc().options.guiScale().set(3);mc().resizeDisplay();mc().options.guiScale().set(2);mc().resizeDisplay();text=mc().screen.children().stream().filter(v->v instanceof MultiLineEditBox).map(v->(MultiLineEditBox)v).findFirst().orElseThrow();check(text.getValue().equals(unsaved),"Resizing preserves the unsaved JSON");click("取消");check(editor.draft.data.equals(original)&&EditorRuntime.project().recipes.isEmpty(),"Preview and cancel leave the draft and pending rules unchanged");
        check(EditorRuntime.recipe("expansion:generic").data().equals(original),"Generating a draft preserves the original recipe");editor.openRawJson();text=mc().screen.children().stream().filter(v->v instanceof MultiLineEditBox).map(v->(MultiLineEditBox)v).findFirst().orElseThrow();text.setValue(unsaved);click("检查并保存");check(mc().screen==editor&&editor.draft.data.getAsJsonObject("settings").get("work").getAsInt()==91,"Saving merged JSON updates the active draft");var saved=ProjectStore.read(root()).recipes.get(copiedId);check(saved!=null&&saved.operation.equals("add")&&saved.source==null,"Saving a generated recipe stages an additive independent rule");var unrelated=new EditorProject.RecipeDraft("expansion:generic",original);unrelated.operation="replace";unrelated.data.getAsJsonObject("settings").addProperty("work",999);EditorRuntime.project().recipes.put(unrelated.id,unrelated);EditorRuntime.saveDraft();click("模式：新建");check(editor.draft.id.equals(copiedId)&&EditorRuntime.project().recipes.get(unrelated.id).data.getAsJsonObject("settings").get("work").getAsInt()==999,"A conflicting target pending rule is retained and the mode remains unchanged");EditorRuntime.project().recipes.remove(unrelated.id);EditorRuntime.saveDraft();click("模式：新建");check(EditorRuntime.project().recipes.containsKey("expansion:generic")&&!EditorRuntime.project().recipes.containsKey(copiedId),"Switching staged new content back moves one pending rule rather than duplicating it");check(editor.draft.data.getAsJsonObject("settings").get("work").getAsInt()==91,"Switching modes preserves the edited document");click("模式：替换");check(!EditorRuntime.project().recipes.containsKey("expansion:generic")&&EditorRuntime.project().recipes.containsKey(copiedId),"Switching back to new restores its ID and moves the staged rule");check(EditorRuntime.recipe("expansion:generic").data().equals(original),"Saving a generated recipe still preserves the source");copiedForApply=dev.kubejseditor.storage.WorkspaceState.copy(editor.draft,EditorProject.RecipeDraft.class);start();
    }
    private static void start()throws Exception{
        EditorRuntime.project().recipes.clear();EditorRuntime.saveDraft();realMods();var data=EditorRuntime.recipe("expansion:generic").data();
        var sourceManager=mc().getSingleplayerServer().getRecipeManager();var sourceHolder=sourceManager.byKey(ResourceLocation.parse("expansion:generic")).orElseThrow();
        @SuppressWarnings("rawtypes") com.mojang.serialization.Codec sourceCodec=sourceHolder.value().getSerializer().codec().codec();
        var sourceCopy=sourceCodec.parse(mc().level.registryAccess().createSerializationContext(JsonOps.INSTANCE),dev.kubejseditor.export.GenericFormatter.encodeRecipe(sourceHolder)).getOrThrow();
        var copySource=GenericRecipeSources.select(GenericRecipeSources.inspect(sourceCopy,sourceManager,new IdentityHashMap<>()));
        check(copySource!=null&&sourceHolder.id().equals(copySource.id()),"The display-only type guard preserves full native-codec copy matching");
        var empty=new CompatDefinition(data.get("type").getAsString(),"confirmed");empty.autoDiscover=false;CompatibilityRecipes.confirm(empty,data,true);
        CompatibilityScreenOpen.open(new WorkspaceScreen(),"expansion:generic");var editor=(CompatibilityScreen)mc().screen;
        var view=(CompatJeiLayouts.View)field(editor,"view");var output=view.slots().stream().filter(s->s.role()==mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT).findFirst().orElseThrow();
        var method=CompatibilityScreen.class.getDeclaredMethod("screenRect",net.minecraft.client.renderer.Rect2i.class);method.setAccessible(true);var rect=(net.minecraft.client.renderer.Rect2i)method.invoke(editor,output.rect());editor.mouseClicked(rect.getX()+2,rect.getY()+2,0);
        check(mc().screen instanceof FieldPickerScreen,"An unmapped slot opens the field tree directly");geometry();clickPath("/product");check(mc().screen==editor,"Selecting a real object binds the clicked output slot without a role/type wizard");
        var p=(RecipePresentation)field(editor,"presentation");check(p.bindings().stream().anyMatch(b->b.path().equals("/product")&&b.output()),"Manual slot role comes from the clicked JEI output");
        var tag=new CompoundTag();tag.putString("origin","inventory-sample");var sample=new ItemStack(Items.DIAMOND,13);sample.set(DataComponents.CUSTOM_NAME,Component.literal("取样钻石"));sample.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));mc().player.getInventory().setItem(9,sample.copy());String before=stack(mc().player.getInventory().getItem(9)).toString();
        editor.openInventory();geometry();check(mc().screen==editor&&editor.inventoryWindow().isOpen(),"Inventory opens as a non-modal palette on the visual editor");
        var window=editor.inventoryWindow();var copied=window.sample(9);copied.setCount(1);check(mc().player.getInventory().getItem(9).getCount()==13,"Returned samples never alias the player inventory");
        var bounds=window.bounds();editor.mouseClicked(bounds.getX()+8,bounds.getY()+8,0);editor.mouseDragged(12,104,0,0,0);editor.mouseReleased(12,104,0);check(window.bounds().getX()!=bounds.getX()||window.bounds().getY()!=bounds.getY(),"Dragging the title moves the floating palette");
        var dropTarget=editor.dropTargets(sample).stream().filter(t->t.area().contains(rect.getX()+2,rect.getY()+2)).findFirst().orElseThrow();
        // Place the palette away from the target before exercising the real screen handlers.
        bounds=window.bounds();editor.mouseClicked(bounds.getX()+8,bounds.getY()+8,0);editor.mouseDragged(editor.width-20,editor.height-20,0,0,0);editor.mouseReleased(editor.width-20,editor.height-20,0);
        var from=window.slotBounds(9);var to=dropTarget.area();editor.mouseClicked(from.getX()+5,from.getY()+5,0);editor.mouseDragged(to.getX()+2,to.getY()+2,0,0,0);editor.mouseReleased(to.getX()+2,to.getY()+2,0);
        check(mc().screen==editor&&window.isOpen(),"Dropping a sample edits a visible slot while keeping the palette open");
        check(editor.draft.data.getAsJsonObject("product").get("id").getAsString().equals("minecraft:diamond"),"Backpack sampling writes the target item ID");check(editor.draft.data.getAsJsonObject("product").has("components")&&editor.draft.data.toString().contains("inventory-sample"),"Backpack sampling preserves complete custom components");check(stack(mc().player.getInventory().getItem(9)).toString().equals(before),"Sampling leaves inventory count and components unchanged");
        EditorRuntime.project().recipes.clear();EditorRuntime.saveDraft();
        empty=new CompatDefinition(data.get("type").getAsString(),"confirmed");empty.autoDiscover=false;CompatibilityRecipes.confirm(empty,data,true);CompatibilityScreenOpen.open(new WorkspaceScreen(),"expansion:generic");editor=(CompatibilityScreen)mc().screen;
        editor.openInventory();var cell=editor.inventoryWindow().slotBounds(9);editor.mouseClicked(cell.getX()+3,cell.getY()+3,0);editor.mouseReleased(cell.getX()+3,cell.getY()+3,0);check(mc().screen instanceof FieldPickerScreen,"Unmapped backpack sampling opens the same real-field tree");clickPath("/product");click("作为物品输出");check(mc().screen==editor&&editor.draft.data.toString().contains("inventory-sample"),"An explicit raw field accepts a sampled stack without creating a fake automatic mapping");
        check(CompatibilityRecipes.policy(data.get("type").getAsString(),editor.draft.data).fields.isEmpty(),"Raw backpack placement preserves a deliberately empty mapping");EditorRuntime.project().recipes.clear();EditorRuntime.saveDraft();
        var workspace=new WorkspaceScreen();mc().setScreen(workspace);click("修改现有");geometry();click("生物掉落");check(mc().screen instanceof ChoiceScreen,"Entity loot browser lists registered entities with real table sources");
        text("搜索","minecraft:cow");check(mc().screen.children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().contains("minecraft:cow")),"Vanilla cow is discoverable by its registered entity ID");
        var lootScreen=new LootTableScreen(new WorkspaceScreen(),TARGET);mc().setScreen(lootScreen);originalLoot=lootScreen.draft.data.deepCopy();check(lootScreen.draft.registryTarget.reloadable(),"Native loot metadata declares reload rather than restart");geometry();click("新增掉落");check(mc().screen instanceof LootEntryScreen,"Loot table provides an item/count/chance form");
        click("背包取样（保留组件）");pickSample();text("数量下限","2");text("数量上限","2");text("独立基础概率 0～1","1");click("检查并保存");check(mc().screen==lootScreen&&LootEntries.items(lootScreen.draft.data).size()==2,"New sampled drop stages alongside the original conditional drop");
        check(lootScreen.draft.data.toString().contains("minecraft:killed_by_player"),"Original player-kill conditions survive adding another drop");
        for(int scale:List.of(2,3)){mc().options.guiScale().set(scale);mc().resizeDisplay();geometry();}mc().options.guiScale().set(2);mc().resizeDisplay();
        var target=new RegistryEntryTarget("minecraft:item_modifier",MODIFIER);var entry=RegistryDataSources.read(target);check(entry!=null&&entry.target().valueEnvelope,"Native array modifiers import without converting their root format");
        var draft=new EditorProject.RecipeDraft(entry.target().id(),entry.data());draft.registryTarget=entry.target();draft.operation="replace";draft.source=entry.data().deepCopy();draft.origin="existing";draft.forceNative=true;
        var next=draft.data.deepCopy();CompatJson.set(next,"/value/0/count",new JsonPrimitive(7));EditorRuntime.validatePatch(draft,next);draft.data=next;EditorRuntime.project().recipes.put(draft.id,draft);
        var predicate=RegistryDataSources.read(new RegistryEntryTarget("minecraft:predicate","expansion:gate"));var gate=new EditorProject.RecipeDraft(predicate.target().id(),predicate.data());gate.registryTarget=predicate.target();gate.operation="replace";gate.source=predicate.data().deepCopy();gate.origin="existing";gate.forceNative=true;var gateNext=gate.data.deepCopy();gateNext.addProperty("chance",0);EditorRuntime.validatePatch(gate,gateNext);gate.data=gateNext;EditorRuntime.project().recipes.put(gate.id,gate);
        var disabled=new EditorProject.RecipeDraft();disabled.id="expansion:disabled";disabled.operation="remove";disabled.source=EditorRuntime.recipe(disabled.id).data().deepCopy();EditorRuntime.project().recipes.put(disabled.id,disabled);EditorRuntime.project().recipes.put(copiedForApply.id,copiedForApply);EditorRuntime.saveDraft();
        var exporter=new DatapackExportScreen(lootScreen);mc().setScreen(exporter);geometry();String pending=ProjectStore.JSON.toJson(EditorRuntime.project());var local=exporter.export(false);check(local!=null&&Files.exists(local.resolve("pack.mcmeta")),"Client export produces a complete standalone datapack");exported=exporter.export(true);check(exported!=null&&Files.exists(exported.resolve("data/expansion/loot_table/entities/test.json")),"World export writes native loot tables in the correct directory");
        check(JsonParser.parseString(Files.readString(exported.resolve("data/expansion/item_modifier/sample.json"))).isJsonArray(),"Exported modifiers have their original native array root");check(ProjectStore.JSON.toJson(EditorRuntime.project()).equals(pending),"Export does not apply or mutate pending rules");
        var review=EditorRuntime.review();check(!review.restartRequired(),"Recipes and reloadable loot/modifier data apply without a JVM restart");EditorRuntime.apply(review);result=null;phase=2;EditorRuntime.reloadResult(false,r->result=r);
    }
    private static void realMods(){for(String namespace:List.of("create","powah","actuallyadditions")){if(!net.neoforged.fml.ModList.get().isLoaded(namespace))continue;int verified=0;for(var holder:mc().getSingleplayerServer().getRecipeManager().getRecipes().stream().sorted(Comparator.comparing(h->h.id().toString())).toList()){
        if(!holder.id().getNamespace().equals(namespace))continue;var loaded=EditorRuntime.recipe(holder.id().toString());if(loaded==null||loaded.data()==null||!loaded.type().startsWith(namespace+":"))continue;
        for(var f:NumericParameters.fields(loaded.data()))if(f.path().toLowerCase(Locale.ROOT).matches(".*(?:energy|time|duration|work).*")&&!f.value().equals("0")){try{var d=new EditorProject.RecipeDraft(loaded.id(),loaded.data());d.forceNative=true;d.operation="replace";var changed=NumericParameters.apply(d.data,Map.of(f.path(),new java.math.BigDecimal(f.value()).multiply(java.math.BigDecimal.TWO).toString()));EditorRuntime.validatePatch(d,changed);check(true,"Real native recipe still edits through shared data: "+loaded.id()+" "+f.path());verified++;break;}catch(RuntimeException unsupported){}}
        if(verified>=3)break;
    }check(verified>=3,"Three native serializers/recipes verified for "+namespace);}}
    private static void actualDrops(boolean edited)throws Exception{
        var server=mc().getSingleplayerServer();var outcomes=server.submit(()->{
            var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();var cow=EntityType.COW.create(level);var pos=player.position().add(3,0,3);cow.moveTo(pos.x,pos.y,pos.z,0,0);var nbt=new CompoundTag();nbt.putString("DeathLootTable",LOOT);cow.readAdditionalSaveData(nbt);cow.setHealth(1);level.addFreshEntity(cow);cow.hurt(level.damageSources().playerAttack(player),100);
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos.add(-2,-2,-2),pos.add(2,2,2)));var values=drops.stream().map(e->e.getItem().copy()).toList();drops.forEach(Entity::discard);
            var modifier=level.getServer().reloadableRegistries().get().registryOrThrow(Registries.ITEM_MODIFIER).get(ResourceLocation.parse(MODIFIER));
            var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,pos).create(LootContextParamSets.CHEST);var context=new LootContext.Builder(params).create(Optional.empty());var changed=modifier.apply(new ItemStack(Items.STONE),context);return new Object[]{values,changed.getCount()};
        }).get(15,TimeUnit.SECONDS);
        @SuppressWarnings("unchecked") var drops=(List<ItemStack>)outcomes[0];int diamonds=drops.stream().filter(s->s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();check(diamonds==(edited?2:0),"Actual entity death produces expected sampled drop count (edited="+edited+")");
        int iron=drops.stream().filter(s->s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum();check(iron==(edited?0:2),"The referenced native predicate controls actual drops (edited="+edited+")");
        if(edited)check(drops.stream().filter(s->s.is(Items.DIAMOND)).allMatch(s->s.has(DataComponents.CUSTOM_DATA)&&s.get(DataComponents.CUSTOM_DATA).copyTag().getString("origin").equals("inventory-sample")),"Actual dropped items retain sampled custom data");
        check((Integer)outcomes[1]==(edited?7:3),"The actual native item modifier uses the expected count (edited="+edited+")");
    }
    private static void applied()throws Exception{
        check(result.success(),"Combined recipe and loot transaction reloads successfully: "+result.message());actualDrops(true);check(mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(copiedId)).isPresent(),"A toggled new recipe is actually present after native application");var active=dev.kubejseditor.storage.WorkspaceState.copy(EditorRuntime.state().applied.recipes.get(copiedId),EditorProject.RecipeDraft.class);active.operation="replace";var newCopy=RecipeModes.toggle(active,id->mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent());check(RecipeModes.toggle(newCopy,id->mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent()).id.equals(copiedId),"Reopened applied new recipe toggles back to itself rather than its former template");check(mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse("expansion:disabled")).isEmpty(),"Disabled recipe is absent after the ordinary transaction");
        EditorRuntime.rollback();check(!EditorRuntime.requiresRestart(),"Rolling back reloadable data retains the reload path");result=null;phase=3;EditorRuntime.reloadResult(false,r->result=r);
    }
    private static void rolledBack()throws Exception{check(result.success(),"Reload after full file rollback succeeds");actualDrops(false);check(EditorRuntime.project().recipes.size()==5,"Rollback preserves the submitted pending rules for review");var review=EditorRuntime.review();check(!review.restartRequired(),"Rolled-back native data can be reapplied without a restart");EditorRuntime.apply(review);result=null;phase=4;EditorRuntime.reloadResult(false,r->result=r);}
    private static void reapplied()throws Exception{check(result.success(),"Reapplying the rolled-back transaction succeeds");actualDrops(true);result=null;phase=5;EditorRuntime.revokeChanges(new LinkedHashSet<>(EditorRuntime.state().applied.recipes.keySet()),Set.of(),r->result=r);}
    private static void revoked()throws Exception{
        check(result.success(),"Batch revoke restores native loot, modifier and recipe sources");actualDrops(false);check(EditorRuntime.state().applied.recipes.isEmpty(),"Revoke clears all editor ownership rules");
        var server=mc().getSingleplayerServer();server.getPackRepository().reload();packId="file/"+exported.getFileName();check(server.getPackRepository().getAvailableIds().contains(packId),"The exported pack is independently discoverable by the world pack repository");var selected=new ArrayList<>(server.getPackRepository().getSelectedIds());selected.add(packId);externalReload=server.reloadResources(selected);phase=6;
    }
    private static void exported()throws Exception{externalReload.get();actualDrops(true);check(EditorRuntime.state().applied.recipes.isEmpty(),"Standalone pack works while editor applied rules remain empty");check(mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse("expansion:disabled")).isEmpty(),"Native false-condition recipe export disables the recipe without a generated script");write(null);phase=99;mc().stop();}
    private static void write(Throwable error)throws Exception{var report=new JsonObject();report.addProperty("passed",error==null);report.addProperty("checks",checks);report.addProperty("screenshots",false);report.add("details",notes);if(exported!=null)report.addProperty("exported",exported.toString());if(error!=null){var trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));report.addProperty("error",trace.toString());}Files.writeString(root().resolve("expansion-verification.json"),ProjectStore.JSON.toJson(report));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!Boolean.getBoolean("kubejs.editor.verifyExpansion")||phase==99)return;ticks++;try{
        if(ticks>12000)throw new AssertionError("Expansion verification timed out at "+phase);
        if(phase==0&&mc().screen instanceof TitleScreen&&ticks>80){mc().createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;}
        else if(phase==1&&mc().level!=null&&mc().player!=null&&WorkspaceJeiPlugin.runtime!=null&&ticks>180)opening();
        else if(phase==10&&mc().screen instanceof CompatibilityScreen)documents();
        else if(phase==2&&result!=null&&!EditorRuntime.busy())applied();else if(phase==3&&result!=null&&!EditorRuntime.busy())rolledBack();else if(phase==4&&result!=null&&!EditorRuntime.busy())reapplied();else if(phase==5&&result!=null&&!EditorRuntime.busy())revoked();else if(phase==6&&externalReload!=null&&externalReload.isDone()&&WorkspaceJeiPlugin.runtime!=null)exported();
    }catch(Throwable error){error.printStackTrace();try{write(error);}catch(Exception writing){writing.printStackTrace();}phase=99;mc().stop();}}
    private ClientExpansionVerification(){}
}
