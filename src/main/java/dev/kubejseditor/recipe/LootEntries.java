package dev.kubejseditor.recipe;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.compat.*;
import java.math.BigDecimal;
import java.util.*;

/** Item leaves are editable while every surrounding pool, condition and custom function stays intact. */
public final class LootEntries {
    public record Entry(String path,String item,String minimum,String maximum,String chance){}
    public static List<Entry> items(JsonObject table){var out=new ArrayList<Entry>();scan(table,"",0,out);return List.copyOf(out);}
    private static void scan(JsonElement value,String path,int depth,List<Entry> out){
        if(depth>48||out.size()>=4096)return;
        if(value.isJsonObject()){
            var o=value.getAsJsonObject();if("minecraft:item".equals(string(o,"type"))&&o.has("name")&&o.get("name").isJsonPrimitive()){
                String min="1",max="1",chance="1";
                if(o.has("functions")&&o.get("functions").isJsonArray())for(var f:o.getAsJsonArray("functions"))if(f.isJsonObject()&&"minecraft:set_count".equals(string(f.getAsJsonObject(),"function"))){var count=f.getAsJsonObject().get("count");min=max="";if(count!=null&&count.isJsonPrimitive()){min=max=count.getAsString();}else if(count!=null&&count.isJsonObject()&&"minecraft:uniform".equals(string(count.getAsJsonObject(),"type"))){min=number(count.getAsJsonObject().get("min"));max=number(count.getAsJsonObject().get("max"));}}
                if(o.has("conditions")&&o.get("conditions").isJsonArray())for(var c:o.getAsJsonArray("conditions"))if(c.isJsonObject()&&"minecraft:random_chance".equals(string(c.getAsJsonObject(),"condition")))chance=number(c.getAsJsonObject().get("chance"));
                out.add(new Entry(path,o.get("name").getAsString(),min,max,chance));return;
            }
            for(var e:o.entrySet())scan(e.getValue(),path+"/"+CompatJson.escape(e.getKey()),depth+1,out);
        }else if(value.isJsonArray())for(int i=0;i<value.getAsJsonArray().size();i++)scan(value.getAsJsonArray().get(i),path+"/"+i,depth+1,out);
    }
    private static String string(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonPrimitive()?o.get(key).getAsString():"";}
    private static String number(JsonElement value){return value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber()?value.getAsString():"";}
    public static JsonObject edit(JsonObject table,String path,String item,String minimum,String maximum,String chance,JsonObject sampledComponents){
        EditorProject.requireId(item);var next=table.deepCopy();JsonObject leaf;
        if(path==null){leaf=new JsonObject();leaf.addProperty("type","minecraft:item");}
        else{leaf=JsonPatches.read(table,path).getAsJsonObject();if(!"minecraft:item".equals(string(leaf,"type")))throw new IllegalArgumentException("请选择物品掉落节点");}
        leaf.addProperty("name",item);var functions=leaf.has("functions")?leaf.getAsJsonArray("functions").deepCopy():new JsonArray();
        if(!minimum.isBlank()||!maximum.isBlank()){
            var min=new BigDecimal(minimum);var max=new BigDecimal(maximum);
            if(min.signum()<0||max.compareTo(min)<0||max.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE))>0)throw new IllegalArgumentException("数量需为非负整数，且上限不小于下限");
            min.intValueExact();max.intValueExact();
            var set=function(functions,"minecraft:set_count");
            if(min.compareTo(max)==0)set.addProperty("count",min);else{var uniform=new JsonObject();uniform.addProperty("type","minecraft:uniform");uniform.addProperty("min",min);uniform.addProperty("max",max);set.add("count",uniform);}
        }
        if(sampledComponents!=null){var f=function(functions,"minecraft:set_components");f.add("components",sampledComponents.deepCopy());}
        if(!functions.isEmpty())leaf.add("functions",functions);else leaf.remove("functions");
        if(!chance.isBlank()){
            var probability=new BigDecimal(chance);if(probability.signum()<0||probability.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("基础概率需为 0～1");
            var conditions=leaf.has("conditions")?leaf.getAsJsonArray("conditions").deepCopy():new JsonArray();
            long matching=conditions.asList().stream().filter(c->c.isJsonObject()&&"minecraft:random_chance".equals(string(c.getAsJsonObject(),"condition"))).count();
            if(matching>1)throw new IllegalArgumentException("存在多个基础概率条件，请用 JSON 编辑以保留组合含义");
            conditions=without(conditions,"condition","minecraft:random_chance");if(probability.compareTo(BigDecimal.ONE)<0){var c=new JsonObject();c.addProperty("condition","minecraft:random_chance");c.addProperty("chance",probability);conditions.add(c);}
            if(conditions.isEmpty())leaf.remove("conditions");else leaf.add("conditions",conditions);
        }
        if(path==null){var pool=new JsonObject();pool.addProperty("rolls",1);var entries=new JsonArray();entries.add(leaf);pool.add("entries",entries);var pools=next.has("pools")?next.getAsJsonArray("pools").deepCopy():new JsonArray();pools.add(pool);next.add("pools",pools);}else CompatJson.set(next,path,leaf);
        return next;
    }
    private static JsonArray without(JsonArray array,String key,String type){var out=new JsonArray();for(var value:array)if(!value.isJsonObject()||!type.equals(string(value.getAsJsonObject(),key)))out.add(value.deepCopy());return out;}
    private static JsonObject function(JsonArray functions,String type){JsonObject found=null;for(var f:functions)if(f.isJsonObject()&&type.equals(string(f.getAsJsonObject(),"function"))){if(found!=null)throw new IllegalArgumentException("存在多个 "+type+"，请使用 JSON 编辑保留执行顺序");found=f.getAsJsonObject();}if(found==null){found=new JsonObject();found.addProperty("function",type);functions.add(found);}return found;}
    public static JsonObject remove(JsonObject table,String path){var entry=JsonPatches.read(table,path);if(!entry.isJsonObject()||!"minecraft:item".equals(string(entry.getAsJsonObject(),"type")))throw new IllegalArgumentException("请选择物品掉落节点");var next=table.deepCopy();CompatJson.remove(next,path);return next;}
    private LootEntries(){}
}
