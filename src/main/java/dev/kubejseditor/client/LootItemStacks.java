package dev.kubejseditor.client;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.kubejseditor.recipe.LootEntries;
import dev.kubejseditor.recipe.compat.JsonPatches;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class LootItemStacks {
    static JsonObject components(ItemStack stack) {
        var mc = Minecraft.getInstance();
        var encoded = ItemStack.CODEC.encodeStart(mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack)
            .getOrThrow().getAsJsonObject();
        return encoded.has("components") ? encoded.getAsJsonObject("components").deepCopy() : new JsonObject();
    }
    static ItemStack preview(JsonObject table, LootEntries.Entry entry) {
        var id = ResourceLocation.tryParse(entry.item());
        var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) return ItemStack.EMPTY;
        var fallback = new ItemStack(item);
        var leaf = JsonPatches.read(table, entry.path()).getAsJsonObject();
        var components = new JsonObject();
        if (leaf.has("functions")) for (var value : leaf.getAsJsonArray("functions")) {
            if (!value.isJsonObject()) continue;
            var function = value.getAsJsonObject();
            if (function.has("function") && "minecraft:set_components".equals(function.get("function").getAsString())
                && function.has("components") && function.get("components").isJsonObject()) {
                for (var component : function.getAsJsonObject("components").entrySet())
                    components.add(component.getKey(), component.getValue().deepCopy());
            }
        }
        if (components.isEmpty()) return fallback;
        var data = new JsonObject(); data.addProperty("id", entry.item()); data.addProperty("count", 1); data.add("components", components);
        return ItemStack.CODEC.parse(Minecraft.getInstance().level.registryAccess().createSerializationContext(JsonOps.INSTANCE), data)
            .result().orElse(fallback);
    }
    private LootItemStacks() {}
}
