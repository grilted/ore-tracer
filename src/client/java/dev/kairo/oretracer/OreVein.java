package dev.kairo.oretracer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record OreVein(OreType type, List<BlockPos> blocks, Vec3 target, Vec3 labelPosition) {
}
