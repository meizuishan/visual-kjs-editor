package dev.kubejseditor.script;

import com.google.gson.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.storage.ProjectStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Native data has no editor metadata or generated JavaScript; export never overwrites a pack. */
public final class NativeDatapack {
    public record Plan(Map<String,byte[]> files,int recipes,int dataEntries,List<String> warnings){}
    private static byte[] bytes(String text){return text.getBytes(StandardCharsets.UTF_8);}
    public static Plan plan(EditorProject content){
        content.validateStructure();var files=new LinkedHashMap<String,byte[]>();int recipes=0,dataEntries=0;
        for(var draft:content.recipes.values())if(draft.enabled){
            JsonElement data;String path;
            if(draft.registryTarget!=null){var target=draft.registryTarget;target.check();path=target.path().substring(RegistryEntryTarget.PACK.length()+1);data=target.generatedValue(draft);dataEntries++;}
            else{EditorProject.requireId(draft.id);int colon=draft.id.indexOf(':');path="data/"+draft.id.substring(0,colon)+"/recipe/"+draft.id.substring(colon+1)+".json";
                if(draft.operation.equals("remove")){var disabled=new JsonObject();var conditions=new JsonArray();var never=new JsonObject();never.addProperty("type","neoforge:false");conditions.add(never);disabled.add(LoadConditions.KEY,conditions);data=disabled;}
                else data=LoadConditions.effective(CraftingGrid.generated(draft),draft);recipes++;
            }
            if(files.putIfAbsent(path,bytes(ProjectStore.JSON.toJson(data)))!=null)throw new IllegalArgumentException("数据包目标重复："+path);
        }
        if(recipes+dataEntries==0)throw new IllegalArgumentException("没有可导出的配方或原生数据修改");
        var warnings=new ArrayList<String>();
        if(!content.items.isEmpty())warnings.add("物品／方块注册不属于数据包内容，目标实例仍需保留对应注册脚本与依赖。");
        if(!content.configs.isEmpty()||!content.scripts.isEmpty()||!content.assets.isEmpty())warnings.add("全局配置、手写脚本与素材未包含；需要完整规则时使用原有导出。");
        warnings.add("目标实例需安装原配方／数据所属模组；后执行的 KubeJS 脚本或其他修改器仍可覆盖数据包。");
        String guide="KubeJS Editor native data export\nMinecraft 1.21.1 / NeoForge, pack format 48\n\n将整个文件夹放入目标世界 datapacks，启用后执行 /reload。普通数据和掉落表可重载；世界生成及启动期注册表通常需要重启。\n本包不要求安装编辑器；数据引用的模组、注册物品和自定义条件仍需安装。\n\n"+String.join("\n",warnings)+"\n";
        files.put("README.txt",bytes(guide));files.put("pack.mcmeta",bytes("{\"pack\":{\"pack_format\":48,\"description\":\"KubeJS Editor native recipe and data overrides\"}}"));
        return new Plan(Collections.unmodifiableMap(files),recipes,dataEntries,List.copyOf(warnings));
    }
    public static Path write(Path destination,Plan plan)throws IOException{
        var root=destination.toAbsolutePath().normalize();if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(root.toString());
        for(Path cursor=root.getParent();cursor!=null;cursor=cursor.getParent())if(Files.isSymbolicLink(cursor)||Files.exists(cursor,LinkOption.NOFOLLOW_LINKS)&&Files.readAttributes(cursor,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther())throw new IOException("导出路径不能经过链接："+cursor);
        Files.createDirectories(root.getParent());Files.createDirectory(root);
        for(var entry:plan.files.entrySet())if(!entry.getKey().equals("pack.mcmeta"))writeEntry(root,entry.getKey(),entry.getValue());
        writeEntry(root,"pack.mcmeta",plan.files.get("pack.mcmeta"));return root;
    }
    private static void writeEntry(Path root,String relative,byte[] data)throws IOException{
        if(relative.contains("\\")||relative.startsWith("/")||Arrays.stream(relative.split("/",-1)).anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new IOException("无效导出路径");
        var file=root.resolve(relative).normalize();if(!file.startsWith(root))throw new IOException("导出路径越界");Files.createDirectories(file.getParent());Files.write(file,data,StandardOpenOption.CREATE_NEW);
    }
    private NativeDatapack(){}
}
