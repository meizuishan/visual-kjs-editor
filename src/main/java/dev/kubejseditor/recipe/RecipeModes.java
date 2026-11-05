package dev.kubejseditor.recipe;

import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.model.RegistryEntryTarget;
import dev.kubejseditor.storage.WorkspaceState;
import java.util.function.Predicate;

/** Switch only authoring identity; preserve the edited native document and its provenance. */
public final class RecipeModes {
    public static boolean creates(EditorProject.RecipeDraft draft){return "add".equals(draft.operation);}
    public static String label(EditorProject.RecipeDraft draft){return creates(draft)?"模式：新建":"模式：替换";}
    private static String entry(String id){return RegistryEntryTarget.isId(id)?RegistryEntryTarget.parse(id).entry:id;}
    public static EditorProject.RecipeDraft toggle(EditorProject.RecipeDraft current,Predicate<String> exists){
        var mode=current.mode==null||!creates(current)&&!java.util.Objects.equals(current.id,current.mode.replaceId)?new EditorProject.RecipeModeState():WorkspaceState.copy(current.mode,EditorProject.RecipeModeState.class);
        if(creates(current)){
            if(mode.replaceId==null)throw new IllegalArgumentException("请先选择要替换的配方 ID");
            if(!exists.test(mode.replaceId))throw new IllegalArgumentException("原来源已不再加载，请重新选择配方");
            var next=current.copy(entry(mode.replaceId));mode.newId=current.id;next.mode=mode;next.operation="replace";next.origin=mode.origin;next.source=mode.source==null?null:mode.source.deepCopy();return next;
        }
        if(mode.replaceId==null){mode.replaceId=current.id;mode.origin=current.origin;mode.source=current.source==null?null:current.source.deepCopy();}
        String base="kubejs_editor:copy/"+entry(mode.replaceId).replace(':','/');
        EditorProject.RecipeDraft next=null;
        if(mode.newId!=null){var retained=current.copy(entry(mode.newId));if(!exists.test(retained.id))next=retained;}
        for(int i=1;next==null&&i<=10000;i++){var candidate=current.copy(base+(i==1?"":"_"+i));if(!exists.test(candidate.id))next=candidate;}
        if(next==null)throw new IllegalArgumentException("无法分配未占用的新配方 ID");
        mode.newId=next.id;next.mode=mode;next.operation="add";next.origin="add";next.source=null;return next;
    }
    private RecipeModes(){}
}
