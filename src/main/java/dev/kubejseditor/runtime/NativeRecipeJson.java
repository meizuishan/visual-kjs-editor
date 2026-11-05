package dev.kubejseditor.runtime;

import com.google.gson.JsonParser;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.kubejs.recipe.schema.UnknownKubeRecipe;
import dev.latvian.mods.kubejs.script.SourceLine;
import dev.kubejseditor.recipe.LoadConditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

/** Pass an exact JSON document through KubeJS's raw recipe carrier and the native codec. */
public final class NativeRecipeJson {
    public static void add(RecipesKubeEvent event,String id,String json){
        var data=JsonParser.parseString(json).getAsJsonObject();
        if(!net.neoforged.neoforge.common.conditions.ICondition.conditionsMatched(event.ops.json(),data))return;
        var body=LoadConditions.body(data);
        Recipe.CODEC.parse(event.ops.json(),body).getOrThrow();
        var recipe=new UnknownKubeRecipe();recipe.id=ResourceLocation.parse(id);recipe.type=event.getRecipeFunction(body.get("type").getAsString());
        recipe.newRecipe=true;recipe.changed=true;recipe.originalJson=body.deepCopy();recipe.json=body;recipe.sourceLine=SourceLine.UNKNOWN;
        event.addRecipe(recipe,true);
    }
    private NativeRecipeJson(){}
}
