package dev.kubejseditor.client;

import com.google.gson.JsonObject;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.CraftingGrid;
import dev.kubejseditor.script.*;
import dev.kubejseditor.storage.ProjectStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.*;

public final class RecipeDocuments {
    public static void open(Screen parent,EditorProject.RecipeDraft draft,Consumer<JsonObject> save){var views=new LinkedHashMap<String,Function<String,String>>();views.put("生成代码",text->RecipeDocumentViews.code(draft,text));views.put("差异",text->RecipeDocumentViews.diff(draft.source,text));Minecraft.getInstance().setScreen(new CompatValueScreen(parent,"配方代码 / JSON · "+draft.id,ProjectStore.JSON.toJson(CraftingGrid.generated(draft)),true,save==null?null:text->save.accept(ValueDocument.parse(text).getAsJsonObject()),views));}
    private RecipeDocuments(){}
}
