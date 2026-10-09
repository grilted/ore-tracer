package dev.kairo.oretracer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class OreScanner {
    private static final int BLOCKS_PER_TICK = 12_000;

    private final OreTracerConfig config;
    private Map<Long, OreType> found = Collections.emptyMap();
    private Map<Long, OreType> building = new HashMap<>();
    private BlockPos center = BlockPos.ZERO;
    private Level scannedLevel;
    private int scanIndex;
    private int diameter;
    private long version;

    public OreScanner(OreTracerConfig config) {
        this.config = config;
    }

    public void tick(Minecraft minecraft) {
        if (!config.enabled || minecraft.level == null || minecraft.player == null) {
            return;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        if (scannedLevel != minecraft.level || scanIndex >= totalBlocks()
                || center.distSqr(playerPos) > 64.0) {
            beginScan(minecraft.level, playerPos);
        }

        int radius = config.scanRadius;
        int max = Math.min(totalBlocks(), scanIndex + BLOCKS_PER_TICK);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (; scanIndex < max; scanIndex++) {
            int local = scanIndex;
            int dx = local % diameter - radius;
            local /= diameter;
            int dz = local % diameter - radius;
            int dy = local / diameter - radius;

            int y = center.getY() + dy;
            if (y < minecraft.level.getMinY() || y >= minecraft.level.getMaxY()) {
                continue;
            }

            cursor.set(center.getX() + dx, y, center.getZ() + dz);
            if (!minecraft.level.hasChunkAt(cursor)) {
                continue;
            }

            OreType type = OreType.from(minecraft.level.getBlockState(cursor).getBlock());
            if (type != null) {
                building.put(cursor.asLong(), type);
            }
        }

        if (scanIndex >= totalBlocks()) {
            found = Collections.unmodifiableMap(building);
            version++;
        }
    }

    private void beginScan(Level level, BlockPos newCenter) {
        scannedLevel = level;
        center = newCenter.immutable();
        diameter = config.scanRadius * 2 + 1;
        scanIndex = 0;
        building = new HashMap<>();
    }

    private int totalBlocks() {
        return diameter == 0 ? 0 : diameter * diameter * diameter;
    }

    public Map<Long, OreType> found() {
        return found;
    }

    public long version() {
        return version;
    }

    public void clear() {
        found = Collections.emptyMap();
        building.clear();
        scannedLevel = null;
        scanIndex = 0;
        diameter = 0;
        version++;
    }
}
