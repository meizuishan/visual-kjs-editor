package dev.kubejseditor.client;

import dev.kubejseditor.model.RegistryEntryTarget;
import dev.kubejseditor.runtime.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.*;
import java.util.*;

/** Entities point to their registered loot table, including shared and modded table IDs. */
public final class LootDataBrowser {
    public static void entities(Screen parent){
        var mc=Minecraft.getInstance();if(!EditorRuntime.canApply()){mc.setScreen(new TextScreen(parent,"生物掉落","需要进入本机世界读取实际掉落数据。"));return;}
        var tables=mc.getSingleplayerServer().reloadableRegistries().getKeys(Registries.LOOT_TABLE);var choices=new ArrayList<ChoiceScreen.Choice>();
        for(var entity:BuiltInRegistries.ENTITY_TYPE){var key=entity.getDefaultLootTable();if(!tables.contains(key.location()))continue;String id=BuiltInRegistries.ENTITY_TYPE.getKey(entity).toString();
            choices.add(new ChoiceScreen.Choice(entity.getDescription().getString()+" · "+id,()->mc.setScreen(new LootTableScreen(parent,new RegistryEntryTarget("minecraft:loot_table",key.location().toString()))),"实际掉落表："+key.location()+"；共享此表的生物都会受影响"));}
        choices.sort(Comparator.comparing(ChoiceScreen.Choice::text));choices.addFirst(new ChoiceScreen.Choice("浏览所有掉落表／谓词／物品修饰器",()->reloadable(parent)));
        mc.setScreen(new ChoiceScreen(parent,"选择生物 · 编辑实际掉落表",choices));
    }
    public static void reloadable(Screen parent){var choices=net.minecraft.world.level.storage.loot.LootDataType.values().map(type->new ChoiceScreen.Choice(type.registryKey().location().toString(),()->entries(parent,type))).toList();Minecraft.getInstance().setScreen(new ChoiceScreen(parent,"可重载原生数据",choices));}
    private static void entries(Screen parent,net.minecraft.world.level.storage.loot.LootDataType<?> type){var mc=Minecraft.getInstance();var choices=mc.getSingleplayerServer().reloadableRegistries().getKeys(type.registryKey()).stream().sorted().map(id->new ChoiceScreen.Choice(id.toString(),()->{var target=new RegistryEntryTarget(type.registryKey().location().toString(),id.toString());if(target.registry.equals("minecraft:loot_table"))mc.setScreen(new LootTableScreen(parent,target));else CompatibilityScreenOpen.open(parent,target.id());})).toList();mc.setScreen(new ChoiceScreen(parent,"选择实际数据 · 重载生效",choices));}
    private LootDataBrowser(){}
}
