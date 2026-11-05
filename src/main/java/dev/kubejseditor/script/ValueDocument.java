package dev.kubejseditor.script;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;

/** Strict JSON tools and bounded text history shared by field and full-document editing. */
public final class ValueDocument {
    public static final int MAX_LENGTH=262144;
    private final Deque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private String text;
    public ValueDocument(String initial){if(initial.length()>MAX_LENGTH)throw new IllegalArgumentException("文本超过编辑上限");text=initial;}
    public String text(){return text;}
    public void change(String next){if(next.length()>MAX_LENGTH)throw new IllegalArgumentException("文本超过编辑上限");if(text.equals(next))return;undo.addLast(text);trim(undo);redo.clear();text=next;}
    private static void trim(Deque<String> history){long bytes=0;for(String entry:history)bytes+=entry.length()*2L;while(history.size()>100||bytes>8*1024*1024){bytes-=history.removeFirst().length()*2L;}}
    public String undo(){if(!undo.isEmpty()){redo.addLast(text);trim(redo);text=undo.removeLast();}return text;}
    public String redo(){if(!redo.isEmpty()){undo.addLast(text);trim(undo);text=redo.removeLast();}return text;}
    public static boolean structured(String text){String s=text.stripLeading();return s.startsWith("{")||s.startsWith("[")||s.startsWith("\"");}
    public static JsonElement parse(String text){
        if(text.length()>MAX_LENGTH)throw new IllegalArgumentException("JSON 超过编辑上限");
        try(var reader=new JsonReader(new StringReader(text))){reader.setLenient(false);var value=read(reader,0);if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("JSON 尾部还有额外内容："+reader);return value;}
        catch(IOException|NumberFormatException error){throw new IllegalArgumentException("JSON 格式错误："+error.getMessage(),error);}
    }
    private static JsonElement read(JsonReader reader,int depth)throws IOException{
        if(depth>64)throw new IllegalArgumentException("JSON 嵌套超过 64 层："+reader);
        return switch(reader.peek()){
            case BEGIN_OBJECT->{var result=new JsonObject();reader.beginObject();while(reader.hasNext()){String key=reader.nextName();if(result.has(key))throw new IllegalArgumentException("JSON 重复键："+key+"；"+reader);result.add(key,read(reader,depth+1));}reader.endObject();yield result;}
            case BEGIN_ARRAY->{var result=new JsonArray();reader.beginArray();while(reader.hasNext())result.add(read(reader,depth+1));reader.endArray();yield result;}
            case STRING->new JsonPrimitive(reader.nextString());
            case NUMBER->new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN->new JsonPrimitive(reader.nextBoolean());
            case NULL->{reader.nextNull();yield JsonNull.INSTANCE;}
            default->throw new IllegalArgumentException("JSON 缺少有效值："+reader);
        };
    }
    public static String format(String text){return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create().toJson(parse(text));}
}
