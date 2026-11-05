package dev.kubejseditor.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import java.lang.reflect.Modifier;
import java.util.*;

/** Public IDs on a display projection are candidates, not proof of a complete source relationship. */
public final class PublicRecipeIdentifiers {
    private static final List<String> NAMES=List.of("getSourceRecipeId","sourceRecipeId","getRecipeId","recipeId","getId","id");
    public static List<ResourceLocation> candidates(Object display,RecipeManager manager){
        var ids=new LinkedHashSet<ResourceLocation>();if(display==null)return List.of();
        for(String name:NAMES)try{var method=display.getClass().getMethod(name);if(Modifier.isStatic(method.getModifiers())||method.getParameterCount()!=0||method.getReturnType()!=ResourceLocation.class)continue;var id=(ResourceLocation)method.invoke(display);if(id!=null&&manager.byKey(id).isPresent())ids.add(id);}catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
        return List.copyOf(ids);
    }
    /** An ID alone remains a candidate. Shared public values must independently agree. */
    public static boolean verified(Object display,ResourceLocation id,RecipeManager manager){
        var source=manager.byKey(id).orElse(null);if(source==null)return false;
        var methods=new HashMap<String,java.lang.reflect.Method>();
        for(var m:source.value().getClass().getMethods())if(m.getParameterCount()==0&&!Modifier.isStatic(m.getModifiers()))methods.put(property(m.getName()),m);
        int matches=0,tried=0;boolean structure=false;
        for(var m:display.getClass().getMethods()){
            if(m.getParameterCount()!=0||Modifier.isStatic(m.getModifiers())||m.getDeclaringClass()==Object.class||Set.of("hashCode","toString","getClass").contains(m.getName())||NAMES.contains(m.getName()))continue;
            var other=methods.get(property(m.getName()));if(other==null||++tried>32)continue;
            try{var a=m.invoke(display);var b=other.invoke(source.value());if(a==null||b==null){if(a!=b)return false;continue;}
                boolean useful=a instanceof Number||a instanceof Boolean||a instanceof ResourceLocation||a instanceof Collection<?>||a instanceof net.minecraft.world.item.ItemStack||a instanceof net.neoforged.neoforge.fluids.FluidStack;
                if(!useful)continue;if(!same(a,b,0))return false;matches++;structure|=a instanceof Collection<?> c&&!c.isEmpty()||a instanceof net.minecraft.world.item.ItemStack||a instanceof net.neoforged.neoforge.fluids.FluidStack;
            }catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){return false;}
        }
        return matches>=2&&structure;
    }
    private static String property(String name){if(name.startsWith("get")&&name.length()>3)return Character.toLowerCase(name.charAt(3))+name.substring(4);if(name.startsWith("is")&&name.length()>2)return Character.toLowerCase(name.charAt(2))+name.substring(3);return name;}
    private static boolean same(Object a,Object b,int depth){if(depth>4)return false;if(a instanceof net.minecraft.world.item.ItemStack x&&b instanceof net.minecraft.world.item.ItemStack y)return net.minecraft.world.item.ItemStack.matches(x,y);if(a instanceof net.neoforged.neoforge.fluids.FluidStack x&&b instanceof net.neoforged.neoforge.fluids.FluidStack y)return net.neoforged.neoforge.fluids.FluidStack.matches(x,y);if(a instanceof net.minecraft.world.item.crafting.Ingredient x&&b instanceof net.minecraft.world.item.crafting.Ingredient y){var ops=net.minecraft.client.Minecraft.getInstance().level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);var ex=net.minecraft.world.item.crafting.Ingredient.CODEC.encodeStart(ops,x).getOrThrow();var ey=net.minecraft.world.item.crafting.Ingredient.CODEC.encodeStart(ops,y).getOrThrow();if(dev.kubejseditor.recipe.compat.CompatJson.equal(ex,ey))return true;if(custom(ex)||custom(ey))return false;var xs=x.getItems();var ys=y.getItems();return xs.length==ys.length&&java.util.Arrays.stream(xs).allMatch(v->java.util.Arrays.stream(ys).anyMatch(w->net.minecraft.world.item.ItemStack.matches(v,w)));}if(a!=null&&b!=null&&a.getClass()==b.getClass()&&a.getClass().isRecord()){try{for(var field:a.getClass().getRecordComponents())if(!same(field.getAccessor().invoke(a),field.getAccessor().invoke(b),depth+1))return false;return true;}catch(ReflectiveOperationException e){return false;}}if(a instanceof Collection<?> x&&b instanceof Collection<?> y){if(x.size()!=y.size()||x.size()>128)return false;var xi=x.iterator();var yi=y.iterator();while(xi.hasNext())if(!same(xi.next(),yi.next(),depth+1))return false;return true;}return Objects.equals(a,b);}
    private static boolean custom(com.google.gson.JsonElement value){if(value.isJsonArray())return value.getAsJsonArray().asList().stream().anyMatch(PublicRecipeIdentifiers::custom);return value.isJsonObject()&&value.getAsJsonObject().has("type");}
    private PublicRecipeIdentifiers(){}
}
