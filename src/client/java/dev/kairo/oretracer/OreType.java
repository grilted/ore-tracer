package dev.kairo.oretracer;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;

public enum OreType {
    COAL("Coal Ore", 0x3A3A3A, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
    IRON("Iron Ore", 0xE3A77B, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
    COPPER("Copper Ore", 0xD97745, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
    GOLD("Gold Ore", 0xFFD83D, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE),
    REDSTONE("Redstone Ore", 0xFF3030, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
    LAPIS("Lapis Ore", 0x356BFF, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
    DIAMOND("Diamond Ore", 0x43E8E8, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
    EMERALD("Emerald Ore", 0x39E675, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
    NETHER_GOLD("Nether Gold Ore", 0xF4C536, Blocks.NETHER_GOLD_ORE),
    QUARTZ("Nether Quartz Ore", 0xF1E7D2, Blocks.NETHER_QUARTZ_ORE),
    ANCIENT_DEBRIS("Ancient Debris", 0xA66A52, Blocks.ANCIENT_DEBRIS),
    SIFTITE("Siftite Ore", 0x53D4B4),
    CHAROITE("Charoite Ore", 0xB178ED);

    private static final Map<Block, OreType> BY_BLOCK = new IdentityHashMap<>();

    static {
        for (OreType type : values()) {
            for (Block block : type.blocks) {
                BY_BLOCK.put(block, type);
            }
        }
    }

    private final String displayName;
    private final int color;
    private final Block[] blocks;

    OreType(String displayName, int color, Block... blocks) {
        this.displayName = displayName;
        this.color = color;
        this.blocks = blocks;
    }

    public String displayName() {
        return displayName;
    }

    public int color() {
        return color;
    }

    public float red() {
        return ((color >> 16) & 0xFF) / 255.0F;
    }

    public float green() {
        return ((color >> 8) & 0xFF) / 255.0F;
    }

    public float blue() {
        return (color & 0xFF) / 255.0F;
    }

    public static OreType from(Block block) {
        return BY_BLOCK.get(block);
    }

    /** Resolve optional blocks only after all mods have registered their content. */
    public static void registerOptionalOres() {
        registerSiftOre("siftslate_coal_ore", COAL);
        registerSiftOre("siftslate_diamond_ore", DIAMOND);
        registerSiftOre("siftslate_emerald_ore", EMERALD);
        registerSiftOre("siftslate_siftite_ore", SIFTITE);
        registerSiftOre("siftslate_charoite_ore", CHAROITE);
    }

    private static void registerSiftOre(String path, OreType type) {
        BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("the_sift", path))
                .ifPresent(holder -> BY_BLOCK.put(holder.value(), type));
    }
}
