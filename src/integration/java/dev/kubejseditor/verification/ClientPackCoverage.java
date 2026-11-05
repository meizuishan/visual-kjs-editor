package dev.kubejseditor.verification;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.kubejseditor.client.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Every JEI entry is source-checked; changes are validated in memory. Writes only the isolated audit. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientPackCoverage {
    private static Set<String> uiCategories=Set.of(),allTransactionCategories=Set.of();
    private static Set<String> requested(String name)throws IOException{var result=new HashSet<String>();var file=root().resolve(name);if(Files.exists(file))for(var v:JsonParser.parseString(Files.readString(file)).getAsJsonArray())result.add(v.getAsString());return result;}
    private static int phase,ticks,categoryIndex,entryIndex;private static long started;
    private static List<IRecipeCategory<?>> categories;private static List<?> entries;private static JsonObject category;
    private static final JsonObject report=new JsonObject();private static final JsonArray rows=new JsonArray();
    private static final Map<String,Probe> probes=new HashMap<>();private static final Map<String,Integer> counts=new TreeMap<>();
    private static final Map<String,EditorProject.RecipeDraft> transactions=new LinkedHashMap<>();private static final Map<String,String> transactionCategories=new LinkedHashMap<>();
    private static BufferedWriter journal;private static EditorRuntime.ReloadResult reload;private static int fieldSamples;private static String retryBlockedId;private static int isolatedFailures;
    private record Probe(String status,String type,String reason,String changedPath,boolean registry){}
    private static Minecraft mc(){return Minecraft.getInstance();}private static Path root(){return mc().gameDirectory.toPath();}
    private static void save()throws IOException{report.addProperty("complete",phase>=3);report.addProperty("categoriesCompleted",categoryIndex);report.addProperty("uniqueSourcesChecked",probes.size());report.add("entryCounts",ProjectStore.JSON.toJsonTree(counts));report.add("categories",rows);report.addProperty("elapsedSeconds",(System.nanoTime()-started)/1_000_000_000L);Files.writeString(root().resolve("pack-coverage.json"),ProjectStore.JSON.toJson(report));if(journal!=null)journal.flush();}
    private static void count(JsonObject row,String key){row.addProperty(key,row.has(key)?row.get(key).getAsInt()+1:1);}
    private static String message(Throwable e){String text=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());for(int i=0;i<4&&e.getCause()!=null;i++){e=e.getCause();text+=" <- "+e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());}return text;}
    private static boolean trapSerializerInvoked;
    private static final class DisplayOnlyRecipe implements net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.RecipeInput>{
        public boolean matches(net.minecraft.world.item.crafting.RecipeInput input,net.minecraft.world.level.Level level){return false;}
        public ItemStack assemble(net.minecraft.world.item.crafting.RecipeInput input,net.minecraft.core.HolderLookup.Provider lookup){return ItemStack.EMPTY;}
        public boolean canCraftInDimensions(int width,int height){return false;}
        public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider lookup){return ItemStack.EMPTY;}
        public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer(){trapSerializerInvoked=true;throw new IllegalStateException("Display-only serializer must never initialize");}
        public net.minecraft.world.item.crafting.RecipeType<?> getType(){return null;}
    }
    private static void sourceSafety(){var result=GenericRecipeSources.inspect(new DisplayOnlyRecipe(),mc().getSingleplayerServer().getRecipeManager(),new IdentityHashMap<>());if(trapSerializerInvoked||!result.isEmpty())throw new AssertionError("Display-only Recipe triggered serializer initialization or a fabricated source");report.addProperty("displayOnlySerializerGuardPassed",true);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void selectedDisplayChecks()throws Exception{
        var file=root().resolve("coverage-display-probes.json");if(!Files.exists(file))return;var checks=new JsonArray();var manager=WorkspaceJeiPlugin.runtime.getRecipeManager();
        for(var id:JsonParser.parseString(Files.readString(file)).getAsJsonArray()){
            IRecipeCategory c=manager.createRecipeCategoryLookup().includeHidden().get().filter(v->v.getRecipeType().getUid().toString().equals(id.getAsString())).findFirst().orElseThrow();
            for(Object value:(List<?>)manager.createRecipeLookup(c.getRecipeType()).includeHidden().get().limit(3).toList()){
                IRecipeLayoutDrawable layout=(IRecipeLayoutDrawable)manager.createRecipeLayoutDrawable(c,value,WorkspaceJeiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();var source=JeiRecipeSources.resolve(layout);var check=new JsonObject();check.addProperty("category",id.getAsString());check.addProperty("displayClass",value.getClass().getName());check.addProperty("source",String.valueOf(source.id()));check.addProperty("reason",source.reason());checks.add(check);
            }
        }report.add("selectedDisplayChecks",checks);
    }
    private static void draftSnapshot(String label)throws Exception{
        var memory=ProjectStore.JSON.toJsonTree(EditorRuntime.project());var disk=ProjectStore.JSON.toJsonTree(ProjectStore.read(root()));var checks=report.has("draftSyncChecks")?report.getAsJsonObject("draftSyncChecks"):new JsonObject();checks.addProperty(label,memory.equals(disk));report.add("draftSyncChecks",checks);
        if(!memory.equals(disk)){Files.writeString(root().resolve("draft-memory-"+label+".json"),ProjectStore.JSON.toJson(memory));Files.writeString(root().resolve("draft-disk-"+label+".json"),ProjectStore.JSON.toJson(disk));}
    }
    private static void note(JsonObject row,JsonObject example){var examples=row.has("examples")?row.getAsJsonArray("examples"):new JsonArray();if(examples.size()<16)examples.add(example.deepCopy());row.add("examples",examples);}
    private static EditorProject.RecipeDraft draft(String id){var loaded=EditorRuntime.recipe(id);if(loaded==null||loaded.data()==null)throw new IllegalArgumentException(loaded==null?"Source not found":loaded.error());var d=new EditorProject.RecipeDraft(id,loaded.data().deepCopy());d.registryTarget=loaded.registryTarget();d.forceNative=true;d.operation="replace";d.origin="existing";d.source=d.data.deepCopy();return d;}
    private static JsonObject mutation(EditorProject.RecipeDraft d,String[] path){
        int tried=0;for(var field:NumericParameters.fields(d.data)){if(tried++>=12)break;try{var number=new BigDecimal(field.value());if(number.precision()>30||Math.abs(number.scale())>30)continue;var changed=number.compareTo(BigDecimal.ZERO)>0&&number.compareTo(BigDecimal.ONE)<=0?number.divide(BigDecimal.TWO):number.add(BigDecimal.ONE);var next=NumericParameters.apply(d.data,Map.of(field.path(),changed.toPlainString()));EditorRuntime.validatePatch(d,next);path[0]=field.path();return next;}catch(Exception ignored){}}
        for(var field:JsonPatches.fields(d.data))if(field.value().isJsonPrimitive()&&!field.path().equals("/type")&&!field.path().startsWith("/neoforge:conditions/")){
            var primitive=field.value().getAsJsonPrimitive();JsonElement replacement=null;
            if(primitive.isBoolean())replacement=new JsonPrimitive(!primitive.getAsBoolean());
            else if(primitive.isString()&&ResourceLocation.tryParse(primitive.getAsString())!=null&&BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(primitive.getAsString())))replacement=new JsonPrimitive(primitive.getAsString().equals("minecraft:stone")?"minecraft:paper":"minecraft:stone");
            if(replacement!=null)try{var next=d.data.deepCopy();CompatJson.set(next,field.path(),replacement);EditorRuntime.validatePatch(d,next);path[0]=field.path();return next;}catch(Exception ignored){}
        }return null;
    }
    private static Probe inspect(String id){return probes.computeIfAbsent(id,key->{try{var d=draft(key);EditorRuntime.validateRecipe(d,Set.of());String[] path={""};var next=mutation(d,path);return new Probe(next==null?"native_json_unproven":"editable",d.data.get("type").getAsString(),next==null?"Native document validates; no accepted scalar/item smoke change found":"",path[0],d.registryTarget!=null);}catch(Exception|LinkageError e){return new Probe("native_rejected","",message(e),"",RegistryEntryTarget.isId(key));}});}
    private static void inventory(EditorProject.RecipeDraft draft)throws Exception{
        var checks=new JsonArray();for(int scale:List.of(2,5)){
            mc().options.guiScale().set(scale);mc().resizeDisplay();var editor=new CompatibilityScreen(null,draft);mc().setScreen(editor);
            for(String label:List.of("背包取样","数值参数","字段补丁","代码 / JSON","模式：替换")){
                var b=editor.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow();
                if(!b.active||b.getX()<0||b.getY()<0||b.getX()+b.getWidth()>editor.width||b.getY()+b.getHeight()>editor.height)throw new AssertionError("Common action unavailable at scale "+scale+": "+label);
            }
            var button=editor.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("背包取样")).map(c->(Button)c).findFirst().orElseThrow();button.onPress();if(mc().screen!=editor||!editor.inventoryWindow().isOpen())throw new AssertionError("Inventory palette does not open above the editor");
            checks.add("Visible common actions and floating inventory palette at requested GUI scale "+scale+", effective "+mc().getWindow().getGuiScale());
        }report.add("inventoryAndToolbar",checks);mc().options.guiScale().set(2);mc().resizeDisplay();mc().setScreen(null);
    }
    private static void press(String label){mc().screen.children().stream().filter(v->v instanceof Button b&&b.getMessage().getString().equals(label)).map(v->(Button)v).findFirst().orElseThrow().onPress();}
    private static void uiEdit(IRecipeLayoutDrawable<?> layout,String id,JsonObject row)throws Exception{
        JeiDisplayScreen.open(null,layout);
        if(mc().screen instanceof JeiDisplayScreen){press("确认原生来源");if(!(mc().screen instanceof ChoiceScreen))throw new AssertionError("Source confirmation did not open candidates");mc().screen.children().stream().filter(v->v instanceof Button b&&!b.getMessage().getString().equals("返回")).map(v->(Button)v).findFirst().orElseThrow().onPress();row.addProperty("actualUiSourceConfirmation",true);}
        if(!(mc().screen instanceof CompatibilityScreen))throw new AssertionError("Actual JEI entry did not open the writable compatibility editor: "+mc().screen.getClass().getSimpleName());
        var editor=(CompatibilityScreen)mc().screen;var edited=draft(id);String[] changedPath={""};var next=mutation(edited,changedPath);if(next==null)throw new AssertionError("No mutation for UI test");editor.openRawJson();var box=mc().screen.children().stream().filter(v->v instanceof MultiLineEditBox).map(v->(MultiLineEditBox)v).findFirst().orElseThrow();box.setValue(ProjectStore.JSON.toJson(next));press("检查并保存");if(mc().screen!=editor||!editor.draft.data.equals(next)||!EditorRuntime.project().recipes.containsKey(id))throw new AssertionError("Actual UI JSON save did not stage the change");row.addProperty("actualUiEditSaved",true);EditorRuntime.project().recipes.clear();EditorRuntime.saveDraft();mc().setScreen(null);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void entry(IRecipeCategory c,Object value)throws Exception{
        var row=new JsonObject();row.addProperty("category",c.getRecipeType().getUid().toString());row.addProperty("index",entryIndex);row.addProperty("displayClass",value.getClass().getName());String status;
        try{
            IRecipeLayoutDrawable layout=(IRecipeLayoutDrawable)WorkspaceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(c,value,WorkspaceJeiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElse(null);if(layout==null)throw new IllegalStateException("JEI declined this display layout");layout.setPosition(0,0);
            boolean input=false,output=false;for(var slot:(List<mezz.jei.api.gui.ingredient.IRecipeSlotView>)layout.getRecipeSlotsView().getSlotViews()){input|=slot.getRole()==RecipeIngredientRole.INPUT;output|=slot.getRole()==RecipeIngredientRole.OUTPUT;}if(input&&output)count(category,"inputOutputEntries");
            var source=JeiRecipeSources.resolve(layout);
            if(source.id()==null&&source.candidates().size()==1&&Files.exists(root().resolve("coverage-confirm-public-id"))){var candidate=source.candidates().getFirst();row.addProperty("explicitlyConfirmedCandidate",candidate.toString());JeiRecipeSources.capture(layout,candidate);source=new JeiRecipeSources.Source(candidate,"Explicit isolated test confirmation",List.of());}
            if(source.id()==null){
                status=source.candidates().isEmpty()?"no_writable_source":"source_confirmation";row.addProperty("reason",source.reason());row.add("candidates",ProjectStore.JSON.toJsonTree(source.candidates().stream().map(Object::toString).toList()));
                if(!source.candidates().isEmpty()){int accepted=0;for(var candidate:source.candidates())if(inspect(candidate.toString()).status.equals("editable"))accepted++;row.addProperty("editableCandidates",accepted);if(source.candidates().size()==1&&accepted==1&&uiCategories.contains(c.getRecipeType().getUid().toString())){String candidate=source.candidates().getFirst().toString();uiEdit(layout,candidate,row);if(allTransactionCategories.contains(c.getRecipeType().getUid().toString())){var change=draft(candidate);String[] path={""};change.data=mutation(change,path);transactions.put(candidate,change);transactionCategories.put(candidate,c.getRecipeType().getUid().toString());}}}
            }else{
                String id=source.id().toString();row.addProperty("source",id);var p=inspect(id);status=p.status;row.addProperty("type",p.type);row.addProperty("reason",p.reason);row.addProperty("changedPath",p.changedPath);row.addProperty("registry",p.registry);if(!p.registry){var key=ResourceLocation.parse(id);row.addProperty("rawRecipeResource",mc().getSingleplayerServer().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(key.getNamespace(),"recipe/"+key.getPath()+".json")).isPresent());}
                if(status.equals("editable")){
                    if(uiCategories.contains(c.getRecipeType().getUid().toString()))uiEdit(layout,id,row);
                    if(!report.has("inventoryAndToolbar"))inventory(draft(id));
                    if(fieldSamples<5){fieldSamples++;var d=draft(id);var slots=new ArrayList<CompatJeiLayouts.Slot>();for(var slot:(List<mezz.jei.api.gui.ingredient.IRecipeSlotView>)layout.getRecipeSlotsView().getSlotViews())if(slot instanceof IRecipeSlotDrawable drawable){var ingredients=slot.getAllIngredients().toList();slots.add(new CompatJeiLayouts.Slot(drawable.getRect(),slot.getRole(),slot.getSlotName().orElse(""),ingredients.stream().anyMatch(v->v.getIngredient() instanceof net.neoforged.neoforge.fluids.FluidStack),ingredients.stream().anyMatch(v->v.getIngredient() instanceof ItemStack),CompatIngredients.signatures(ingredients.stream().map(v->v.getIngredient()).toList())));}
                        Object nativeValue=d.registryTarget==null?mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).map(RecipeHolder::value).orElse(null):RegistryDataSources.read(d.registryTarget).value();var options=AdaptationStrategies.inspect(d.data,new CompatJeiLayouts.View(layout,slots,null),nativeValue);var best=options.getFirst();var mapping=new JsonObject();mapping.addProperty("source",id);mapping.addProperty("matchedSlots",best.matchedSlots());mapping.addProperty("totalSlots",best.slots());mapping.addProperty("writableFields",best.writable());mapping.addProperty("unresolvedFields",best.unresolved());mapping.addProperty("method",best.name());var mappings=category.has("fieldSamples")?category.getAsJsonArray("fieldSamples"):new JsonArray();mappings.add(mapping);category.add("fieldSamples",mappings);
                    }
                    if(!p.registry&&(allTransactionCategories.contains(c.getRecipeType().getUid().toString())||Files.exists(root().resolve("coverage-all-confirmed-transactions"))||!transactionCategories.containsValue(c.getRecipeType().getUid().toString()))){var d=draft(id);String[] path={""};var next=mutation(d,path);if(next!=null){d.data=next;transactions.putIfAbsent(id,d);transactionCategories.putIfAbsent(id,c.getRecipeType().getUid().toString());}}
                }
            }
        }catch(Exception|LinkageError e){status=e instanceof IllegalStateException&&"JEI declined this display layout".equals(e.getMessage())?"layout_unavailable":"audit_error";row.addProperty("reason",message(e));}
        row.addProperty("status",status);count(category,status);counts.merge(status,1,Integer::sum);if(!status.equals("editable")||!category.has("examples"))note(category,row);
        journal.write(new Gson().toJson(row));journal.newLine();
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static boolean nextCategory()throws Exception{
        if(categoryIndex>=categories.size())return false;var c=categories.get(categoryIndex);entries=WorkspaceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(c.getRecipeType()).includeHidden().get().toList();entryIndex=0;fieldSamples=0;
        category=new JsonObject();category.addProperty("id",c.getRecipeType().getUid().toString());category.addProperty("title",c.getTitle().getString());category.addProperty("displayCount",entries.size());category.addProperty("categoryClass",c.getClass().getName());var catalysts=new JsonArray();for(var typed:WorkspaceJeiPlugin.runtime.getRecipeManager().createRecipeCatalystLookup(c.getRecipeType()).includeHidden().get().toList())if(typed.getIngredient() instanceof ItemStack stack)catalysts.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());category.add("catalysts",catalysts);rows.add(category);return true;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void verifyActual(boolean restored)throws Exception{
        var checks=new JsonArray();var server=mc().getSingleplayerServer();var ops=server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for(var d:transactions.values()){var row=new JsonObject();row.addProperty("id",d.id);row.addProperty("category",transactionCategories.get(d.id));try{var holder=server.getRecipeManager().byKey(ResourceLocation.parse(d.id)).orElseThrow();var codec=(com.mojang.serialization.Codec)holder.value().getSerializer().codec().codec();var expected=codec.encodeStart(ops,codec.parse(ops,LoadConditions.body(restored?d.source:d.data)).getOrThrow()).getOrThrow();var actual=codec.encodeStart(ops,holder.value()).getOrThrow();row.addProperty("matches",CompatJson.equal((JsonElement)expected,(JsonElement)actual));}catch(Exception e){row.addProperty("matches",false);row.addProperty("reason",message(e));}checks.add(row);}
        report.add(restored?"restoredActualSources":"appliedActualSources",checks);
    }
    private static void replayTransactions()throws Exception{
        report.addProperty("allJeiEntriesChecked",false);report.addProperty("transactionReplayOnly",true);
        for(var entry:JsonParser.parseString(Files.readString(root().resolve("coverage-transaction-request.json"))).getAsJsonArray()){
            String id=entry.getAsJsonObject().get("id").getAsString();var d=draft(id);String[] path={""};var next=mutation(d,path);if(next!=null){d.data=next;transactions.put(id,d);transactionCategories.put(id,entry.getAsJsonObject().get("category").getAsString());}
        }
        if(!transactions.isEmpty())inventory(transactions.values().iterator().next());beginTransactions();
    }
    private static void beginTransactions()throws Exception{
        report.addProperty("transactionSampleCount",transactions.size());if(transactions.isEmpty()){phase=5;return;}
        EditorRuntime.project().recipes.clear();for(var d:transactions.values())EditorRuntime.project().recipes.put(d.id,WorkspaceState.copy(d,EditorProject.RecipeDraft.class));EditorRuntime.saveDraft();draftSnapshot("saved_"+isolatedFailures);var review=EditorRuntime.review();draftSnapshot("review_"+isolatedFailures);report.addProperty("batchRequiresRestart",review.restartRequired());if(review.restartRequired())throw new IllegalStateException("Ordinary recipe smoke batch unexpectedly requires restart");EditorRuntime.apply(review);draftSnapshot("applied_"+isolatedFailures);reload=null;phase=3;EditorRuntime.reloadResult(false,r->reload=r);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyPackCoverage")||phase==99)return;var mc=mc();ticks++;
        try{
            if(ticks%900==0)System.out.println("[Pack coverage] phase="+phase+" category="+categoryIndex+" entry="+entryIndex+" screen="+(mc.screen==null?"world":mc.screen.getClass().getName()));
            if(phase==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null&&ticks>60){started=System.nanoTime();var input=JsonParser.parseString(Files.readString(root().resolve("pack-audit-input.json"))).getAsJsonObject();report.addProperty("pack",input.get("pack").getAsString());var versions=new JsonObject();for(var mod:ModList.get().getMods())versions.addProperty(mod.getModId(),mod.getVersion().toString());report.add("loadedMods",versions);report.addProperty("editorVersion","0.22.0");report.addProperty("allJeiEntriesChecked",true);report.addProperty("fieldMappingSamplesPerCategory",5);journal=Files.newBufferedWriter(root().resolve("pack-coverage-entries.jsonl"));phase=1;mc.tell(()->mc.createWorldOpenFlows().openWorld("EditorVerification",()->{}));return;}
            if(phase==1&&mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen){mc.screen.children().stream().filter(v->v instanceof Button).map(v->(Button)v).findFirst().orElseThrow().onPress();report.addProperty("isolatedFixtureBackupConfirmed",true);return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&WorkspaceJeiPlugin.runtime!=null&&ticks>160){EditorRuntime.project();if(!EditorRuntime.project().recipes.isEmpty()||!EditorRuntime.state().applied.recipes.isEmpty())throw new IllegalStateException("Audit requires an empty isolated editor workspace");SimpleRecipes.setEnabled(EditorRuntime.root(),true);uiCategories=requested("coverage-ui-categories.json");allTransactionCategories=requested("coverage-all-transaction-categories.json");sourceSafety();selectedDisplayChecks();mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().forEach(player->{player.setInvulnerable(true);player.setNoGravity(true);}));if(Files.exists(root().resolve("coverage-transaction-request.json"))){replayTransactions();return;}categories=WorkspaceJeiPlugin.runtime.getRecipeManager().createRecipeCategoryLookup().includeHidden().get().sorted(Comparator.comparing(c->c.getRecipeType().getUid().toString())).toList();report.addProperty("registeredCategories",categories.size());if(Files.exists(root().resolve("coverage-categories.json"))){var selected=new HashSet<String>();for(var name:JsonParser.parseString(Files.readString(root().resolve("coverage-categories.json"))).getAsJsonArray())selected.add(name.getAsString());categories=categories.stream().filter(c->selected.contains(c.getRecipeType().getUid().toString())).toList();report.addProperty("allJeiEntriesChecked",false);report.addProperty("targetedCategories",categories.size());if(categories.isEmpty())throw new IllegalStateException("No requested JEI categories found");}report.addProperty("nativeRecipeCount",mc.getSingleplayerServer().getRecipeManager().getRecipes().size());phase=2;nextCategory();save();}
            if(phase==2){long deadline=System.nanoTime()+25_000_000;do{if(entryIndex>=entries.size()){category.addProperty("complete",true);System.out.println("[Pack coverage] "+category.get("id")+" "+category.get("title")+" "+entryIndex+" entries; "+category);categoryIndex++;save();if(!nextCategory()){phase=3;save();beginTransactions();break;}}else{entry(categories.get(categoryIndex),entries.get(entryIndex));entryIndex++;}}while(System.nanoTime()<deadline);}
            if(phase==3&&reload!=null&&!EditorRuntime.busy()){report.addProperty("batchReloadSuccess",reload.success());report.addProperty("batchReloadMessage",reload.message());draftSnapshot("reloaded_"+isolatedFailures);verifyActual(false);retryBlockedId=null;if(!reload.success()){var failed=report.has("failedBatches")?report.getAsJsonArray("failedBatches"):new JsonArray();var failure=new JsonObject();failure.addProperty("message",reload.message());var matcher=java.util.regex.Pattern.compile("Multiple entries with same key: ([a-z0-9_.-]+:[a-z0-9_./-]+)").matcher(reload.message());if(matcher.find()&&transactions.containsKey(matcher.group(1))&&isolatedFailures<10){retryBlockedId=matcher.group(1);failure.addProperty("isolatedSource",retryBlockedId);failure.addProperty("category",transactionCategories.get(retryBlockedId));isolatedFailures++;}failed.add(failure);report.add("failedBatches",failed);}EditorRuntime.rollback();reload=null;phase=4;EditorRuntime.reloadResult(false,r->reload=r);save();}
            if(phase==4&&reload!=null&&!EditorRuntime.busy()){report.addProperty("rollbackReloadSuccess",reload.success());report.addProperty("rollbackMessage",reload.message());verifyActual(true);if(retryBlockedId!=null){transactions.remove(retryBlockedId);retryBlockedId=null;beginTransactions();save();return;}phase=5;}
            if(phase==5){report.addProperty("sourceAuditPassed",!counts.containsKey("audit_error"));boolean cycle=transactions.isEmpty()||report.has("batchReloadSuccess")&&report.get("batchReloadSuccess").getAsBoolean()&&report.get("rollbackReloadSuccess").getAsBoolean();report.addProperty("reloadCyclePassed",cycle);boolean actual=transactions.isEmpty()||java.util.stream.Stream.of("appliedActualSources","restoredActualSources").allMatch(key->report.has(key)&&report.getAsJsonArray(key).asList().stream().allMatch(v->v.getAsJsonObject().get("matches").getAsBoolean()));report.addProperty("passed",cycle&&actual&&!counts.containsKey("audit_error"));save();journal.close();phase=99;mc.stop();}
        }catch(Throwable e){if(e instanceof VirtualMachineError)throw (VirtualMachineError)e;report.addProperty("passed",false);report.addProperty("fatal",message(e));report.addProperty("phase",phase);e.printStackTrace();try{save();if(journal!=null)journal.close();}catch(Exception ignored){}phase=99;mc.stop();}
    }
    private ClientPackCoverage(){}
}
