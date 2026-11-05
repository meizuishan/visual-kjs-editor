package dev.kubejseditor.recipe;

import java.util.Set;

/** Duration consumers verified against Create 6.0.10's machine implementations. */
public final class CreateRecipeTiming {
    private static final Set<String> RECIPE_DURATION = Set.of(
        "create:crushing", "create:milling", "create:cutting", "create:mixing");
    private static final Set<String> PRESS_CYCLE = Set.of("create:pressing", "create:compacting");

    private CreateRecipeTiming() {}

    public static boolean usesRecipeDuration(String type) { return RECIPE_DURATION.contains(type); }
    public static boolean usesPressCycle(String type) { return PRESS_CYCLE.contains(type); }
}
