package dev.kubejseditor;

import com.google.gson.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.script.ValueDocument;
import java.util.*;
import java.util.function.Function;

public final class CompatLearningVerification {
    private static int checks;
    private static void check(boolean ok,String note){checks++;if(!ok)throw new AssertionError(note);}
    private static void fails(Runnable action,String note){checks++;try{action.run();}catch(RuntimeException expected){return;}throw new AssertionError(note);}
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    public static int run(){
        Function<JsonObject,JsonObject> codec=input->{var out=new JsonObject();out.add("work",input.get("work"));return out;};
        var loaded=json("{type:'test:native',work:7}");var raw=json("{type:'test:native',work:7,extra:{retain:true},'neoforge:conditions':[]}");
        var chosen=NativeRecipeSource.select(loaded,raw,codec);
        check(chosen.has("extra")&&chosen.has(LoadConditions.KEY),"Matching raw resources retain unknown extensions and conditions");
        chosen.remove("extra");check(raw.has("extra"),"Source imports never alias resource JSON");
        var changed=loaded.deepCopy();changed.addProperty("work",22);
        chosen=NativeRecipeSource.select(changed,raw,codec);check(chosen.get("work").getAsInt()==22&&!chosen.has("extra"),"Runtime changes cannot be replaced by stale resource fields");
        check(chosen.has(LoadConditions.KEY),"Existing load-condition recovery remains intact");
        check(NativeRecipeSource.select(loaded,null,codec).equals(loaded),"Runtime-only recipes still import without a resource");
        raw.addProperty("type","test:other");check(!NativeRecipeSource.select(loaded,raw,codec).has("extra"),"Different serializers cannot donate raw fields");
        raw.addProperty("type","test:native");check(!NativeRecipeSource.select(loaded,raw,input->{throw new IllegalArgumentException("bad source");}).has("extra"),"Failed source decoding falls back to the loaded snapshot");

        var source=json("{type:'test:numeric',energy:'9007199254740993',work:7,nested:[0.125,2],label:'1:20',enabled:true,components:{'x/y~z':12},'neoforge:conditions':[{level:2}]}");
        check(NumericParameters.fields(source).size()==5,"Numeric discovery includes nested values, numeric strings and escaped component keys");
        var edited=NumericParameters.apply(source,Map.of("/energy","9007199254740993123456789","/nested/0","1.25e-3","/work","2147483648"));
        check(edited.get("energy").getAsJsonPrimitive().isString()&&edited.get("energy").getAsString().equals("9007199254740993123456789"),"String numbers never pass through double or lose their string type");
        check(edited.getAsJsonArray("nested").get(0).getAsBigDecimal().toString().equals("0.00125"),"Scientific notation remains an exact decimal");
        check(edited.get("work").getAsLong()==2147483648L&&source.get("work").getAsInt()==7,"Numeric edits preserve large integers and isolate the draft");
        fails(()->NumericParameters.apply(source,Map.of("/work","NaN")),"Nonfinite numbers are rejected");
        fails(()->NumericParameters.apply(source,Map.of("/work","01")),"Invalid JSON numeric syntax is rejected");
        fails(()->NumericParameters.apply(source,Map.of("/label","20")),"Non-numeric text cannot be silently converted");
        fails(()->NumericParameters.apply(source,Map.of("/missing","20")),"Numeric editing cannot invent a schema field");
        fails(()->NumericParameters.apply(source,Map.of("/work","1e9999999999999")),"Unrepresentable decimal exponents fail before mutation");
        check(source.get("energy").getAsString().equals("9007199254740993"),"Failed edits leave the input unchanged");
        var n0=json("{work:7,energy:'1000'}");var n1=json("{work:4294967367,energy:'2000'}");var encoded=json("{work:71,energy:'2000'}");
        fails(()->NumericParameters.verifyRoundTrip(n0,n1,n0,encoded),"A second valid edit cannot hide integer overflow");
        fails(()->NumericParameters.verifyRoundTrip(n0,json("{work:7.9,energy:'2000'}"),n0,json("{work:7,energy:'2000'}")),"Fractional-to-integer truncation is rejected");
        fails(()->NumericParameters.verifyRoundTrip(n0,json("{work:8,energy:'2000'}"),n0,json("{energy:'2000'}")),"Dropping a previously canonical number is rejected");
        NumericParameters.verifyRoundTrip(n0,json("{work:8,energy:'9007199254740993123456789'}"),n0,json("{work:8,energy:'9007199254740993123456789'}"));checks++;
        NumericParameters.verifyRoundTrip(json("{n:0.5}"),json("{n:0.123456789}"),json("{n:0.5}"),json("{n:0.12345679}"));checks++;
        fails(()->NumericParameters.verifyRoundTrip(json("{n:0.5}"),json("{n:1e-100}"),json("{n:0.5}"),json("{n:0}")),"Floating-point underflow cannot masquerade as tolerance");

        String text="{\"文字\":\"深渊\",\"empty\":null,\"large\":9007199254740993123456789}";
        String formatted=ValueDocument.format(text);check(CompatJson.equal(ValueDocument.parse(text),ValueDocument.parse(formatted)),"Formatting preserves Unicode, nulls and huge integers");
        check(formatted.contains("null")&&formatted.contains("9007199254740993123456789"),"JSON formatting never drops explicit null values or rounds integers");
        fails(()->ValueDocument.parse("{a:1}"),"Editor JSON disallows unquoted keys");
        fails(()->ValueDocument.parse("{\"a\":1,\"a\":2}"),"Duplicate keys are rejected rather than silently overwritten");
        fails(()->ValueDocument.parse("{} false"),"Trailing JSON values are rejected");
        fails(()->ValueDocument.parse("{\"a\":NaN}"),"JSON syntax checks reject NaN");
        fails(()->ValueDocument.parse("{\"a\":1,}"),"Trailing commas are diagnosed");
        fails(()->ValueDocument.parse("[".repeat(66)+"0"+"]".repeat(66)),"Deep JSON is bounded");
        try{ValueDocument.parse("{\n\"a\":\n}");throw new AssertionError("Invalid JSON accepted");}catch(IllegalArgumentException error){check(error.getMessage().contains("line"),"Syntax diagnostics retain the line and column from the JSON reader");}
        var history=new ValueDocument(text);history.change(formatted);check(history.undo().equals(text),"Formatting is undoable as one edit");check(history.redo().equals(formatted),"Formatting redo restores the exact text");
        history.undo();history.change("incomplete {");check(history.redo().equals("incomplete {"),"A new edit clears obsolete redo history and retains unfinished text");
        for(int i=0;i<150;i++)history.change("value "+i);for(int i=0;i<150;i++)history.undo();check(history.text()!=null,"Bounded history remains usable beyond its retention limit");
        check(ValueDocument.structured("\n {\"a\":1}")&&!ValueDocument.structured("minecraft:stone"),"Raw IDs keep their plain-text editor behavior");
        return checks;
    }
    private CompatLearningVerification(){}
}
