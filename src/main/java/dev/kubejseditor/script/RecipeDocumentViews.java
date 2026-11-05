package dev.kubejseditor.script;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.storage.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Previews use a copy of the unsaved document and never stage a rule. */
public final class RecipeDocumentViews {
    public static String code(EditorProject.RecipeDraft original,String text){var copy=WorkspaceState.copy(original,EditorProject.RecipeDraft.class);copy.data=ValueDocument.parse(text).getAsJsonObject();copy.grid=null;var single=new EditorProject();single.recipes.put(copy.id,copy);var out=new StringBuilder();KjsGenerator.generate(single).files().entrySet().stream().filter(e->!e.getKey().equals(ProjectStore.FILE)&&!e.getKey().endsWith("/applied-items.json")).forEach(e->{String path=e.getKey();byte[] bytes=e.getValue();out.append("// ").append(path).append('\n').append(new String(bytes,StandardCharsets.UTF_8)).append('\n');});return out.toString();}
    public static String diff(JsonObject original,String text){var next=ValueDocument.parse(text).getAsJsonObject();var lines=new ArrayList<String>();compare(original==null?JsonNull.INSTANCE:original,next,"",lines,0);return lines.isEmpty()?"没有修改":String.join("\n",lines);}
    private static void compare(JsonElement a,JsonElement b,String path,List<String> lines,int depth){if(a.equals(b))return;if(lines.size()>4096||depth>48){if(lines.size()==4097)lines.add("…差异较大，请查看完整 JSON");return;}
        if(a.isJsonObject()&&b.isJsonObject()){var keys=new TreeSet<String>();keys.addAll(a.getAsJsonObject().keySet());keys.addAll(b.getAsJsonObject().keySet());for(String key:keys){String child=path+"/"+key.replace("~","~0").replace("/","~1");if(!a.getAsJsonObject().has(key))lines.add("+ "+child+" = "+b.getAsJsonObject().get(key));else if(!b.getAsJsonObject().has(key))lines.add("- "+child+" = "+a.getAsJsonObject().get(key));else compare(a.getAsJsonObject().get(key),b.getAsJsonObject().get(key),child,lines,depth+1);}}
        else{lines.add("- "+(path.isEmpty()?"/":path)+" = "+a);lines.add("+ "+(path.isEmpty()?"/":path)+" = "+b);}
    }
    private RecipeDocumentViews(){}
}
