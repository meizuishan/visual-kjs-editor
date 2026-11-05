package dev.kubejseditor.recipe.compat;

import com.google.gson.*;
import java.util.*;

/** Navigation and search retain full pointer context instead of repeating ambiguous leaf names. */
public final class FieldTree {
    public record Node(String path,JsonElement value,String hint){public boolean branch(){return value.isJsonArray()||value.isJsonObject();}}
    public static List<Node> nodes(JsonObject root,String scope,String query){
        JsonElement owner=scope.isEmpty()?root:JsonPatches.read(root,scope);var result=new ArrayList<Node>();String search=query.toLowerCase(Locale.ROOT);
        if(!search.isBlank())for(var f:JsonPatches.fields(root)){if((scope.isEmpty()||f.path().startsWith(scope+"/"))&&!f.path().equals("/type")&&(f.path()+" "+f.value()+" "+hint(f.value())).toLowerCase(Locale.ROOT).contains(search))result.add(new Node(f.path(),f.value(),hint(f.value())));}
        else if(owner.isJsonObject())for(var e:owner.getAsJsonObject().entrySet()){String path=scope+"/"+CompatJson.escape(e.getKey());if(!path.equals("/type"))result.add(new Node(path,e.getValue().deepCopy(),hint(e.getValue())));}
        else if(owner.isJsonArray())for(int i=0;i<owner.getAsJsonArray().size();i++){var value=owner.getAsJsonArray().get(i);result.add(new Node(scope+"/"+i,value.deepCopy(),hint(value)));}
        return List.copyOf(result);
    }
    public static String hint(JsonElement value){if(value.isJsonObject()){var o=value.getAsJsonObject();if(o.has("ingredient"))return "原料包装";if(o.has("item")||o.has("id")||o.has("fluid")||o.has("tag"))return "原料／资源对象";return "对象 · "+o.size()+" 个字段";}if(value.isJsonArray())return "列表 · "+value.getAsJsonArray().size()+" 项";if(value.isJsonNull())return "空值";var p=value.getAsJsonPrimitive();return p.isBoolean()?"开关":NumericParameters.numeric(p)?p.isString()?"数值 · 数字字符串":"数值":p.getAsString().matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+")?"资源 ID":"文字";}
    public static String parent(String path){int last=path.lastIndexOf('/');return last<0?"":path.substring(0,last);}
    private FieldTree(){}
}
