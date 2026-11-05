package dev.kubejseditor.recipe.compat;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/** Exact numeric editing without depending on a slot mapping or serializer-specific field names. */
public final class NumericParameters {
    private static final Pattern NUMBER=Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    public record Field(String path,String value,boolean stringEncoded){}
    public static boolean numeric(JsonElement value){
        if(value==null||!value.isJsonPrimitive())return false;
        var p=value.getAsJsonPrimitive();
        return (p.isNumber()||p.isString())&&p.getAsString().length()<=256&&NUMBER.matcher(p.getAsString()).matches();
    }
    public static List<Field> fields(JsonObject source){
        return JsonPatches.fields(source).stream().filter(f->!f.path().equals("/type")&&!f.path().startsWith("/neoforge:conditions/")&&numeric(f.value()))
                .map(f->new Field(f.path(),f.value().getAsString(),f.value().getAsJsonPrimitive().isString())).toList();
    }
    public static JsonObject apply(JsonObject source,Map<String,String> changes){
        var known=new HashMap<String,Field>();fields(source).forEach(f->known.put(f.path(),f));
        var next=source.deepCopy();
        for(var change:changes.entrySet()){
            var field=known.get(change.getKey());String text=change.getValue()==null?"":change.getValue().trim();
            if(field==null||text.length()>256||!NUMBER.matcher(text).matches())throw new IllegalArgumentException("需要有效数值："+change.getKey());
            BigDecimal number;
            try{number=new BigDecimal(text);}catch(NumberFormatException error){throw new IllegalArgumentException("数值超出可表示范围："+change.getKey(),error);}
            CompatJson.set(next,field.path(),field.stringEncoded()?new JsonPrimitive(text):new JsonPrimitive(number));
        }
        return next;
    }
    /** An unrelated successful edit must not hide integer overflow or truncation in another field. */
    public static void verifyRoundTrip(JsonObject before,JsonObject after,JsonObject encodedBefore,JsonObject encodedAfter){
        for(var field:fields(after)){
            var wanted=CompatJson.get(after,field.path());var previous=CompatJson.get(before,field.path());
            if(CompatJson.equal(wanted,previous))continue;
            var oldNative=CompatJson.get(encodedBefore,field.path());
            if(!numeric(oldNative))continue; // A codec may use different canonical paths; do not invent a correspondence.
            var actual=CompatJson.get(encodedAfter,field.path());
            if(!numeric(actual)||!equivalent(new BigDecimal(wanted.getAsString()),new BigDecimal(actual.getAsString())))
                throw new IllegalArgumentException("原生格式未保留数值，可能发生截断或溢出："+field.path()+"（输入 "+wanted+"，原生值 "+actual+"）");
        }
    }
    private static boolean equivalent(BigDecimal expected,BigDecimal actual){
        if(expected.compareTo(actual)==0)return true;
        if(expected.stripTrailingZeros().scale()<=0)return false;
        float f=expected.floatValue();double d=expected.doubleValue();
        return Float.isFinite(f)&&f!=0&&new BigDecimal(Float.toString(f)).compareTo(actual)==0
                ||Double.isFinite(d)&&d!=0&&new BigDecimal(Double.toString(d)).compareTo(actual)==0;
    }
    private NumericParameters(){}
}
