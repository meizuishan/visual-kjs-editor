package dev.kubejseditor.recipe;

import com.google.gson.JsonObject;
import dev.kubejseditor.recipe.compat.CompatJson;
import java.util.Objects;
import java.util.function.Function;

/** Keep original fields only when the resource still describes the loaded recipe. */
public final class NativeRecipeSource {
    public static JsonObject select(JsonObject loaded,JsonObject resource,Function<JsonObject,JsonObject> normalize){
        var fallback=loaded.deepCopy();
        if(resource==null)return fallback;
        LoadConditions.restoreFromSource(fallback,resource);
        if(!Objects.equals(loaded.get("type"),resource.get("type")))return fallback;
        try{
            if(CompatJson.equal(normalize.apply(LoadConditions.body(loaded)),normalize.apply(LoadConditions.body(resource))))return resource.deepCopy();
        }catch(RuntimeException ignored){/* Runtime scripts can replace or invalidate the original resource. */}
        return fallback;
    }
    private NativeRecipeSource(){}
}
