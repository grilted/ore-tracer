package dev.kairo.oretracer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

public final class OreRenderer implements AutoCloseable {
    private static final float BOX_INSET = 0.035F;
    private static final int MAX_RENDERED_BLOCKS = 6_000;
    private static final int FULL_BRIGHT = 0x00F000F0;

    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("oretracer", "pipeline/filled_through_walls"))
                    .withDepthStencilState(Optional.empty())
                    .build());

    private static final RenderPipeline LINES_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("oretracer", "pipeline/lines_through_walls"))
                    .withDepthStencilState(Optional.empty())
                    .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
                    .build());

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1F, 1F, 1F, 1F);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();

    private final StagedVertexBuffer filledBuffer =
            new StagedVertexBuffer(() -> "Ore Tracer filled boxes", RenderType.BIG_BUFFER_SIZE);
    private final StagedVertexBuffer lineBuffer =
            new StagedVertexBuffer(() -> "Ore Tracer outlines", RenderType.BIG_BUFFER_SIZE);
    private final OreTracerConfig config;
    private final OreScanner scanner;
    private long cachedVersion = Long.MIN_VALUE;
    private List<OreVein> cachedVeins = List.of();
    private volatile RenderState renderState = new RenderState(List.of());

    public OreRenderer(OreTracerConfig config, OreScanner scanner) {
        this.config = config;
        this.scanner = scanner;
    }

    public void extract(LevelExtractionContext context) {
        if (!config.enabled) {
            renderState = new RenderState(List.of());
            return;
        }

        rebuildVeinsIfNeeded();
        Vec3 camera = context.camera().position();
        List<OreVein> visible = cachedVeins.stream()
                .filter(vein -> config.isEnabled(vein.type()))
                .sorted(Comparator.comparingDouble(vein -> vein.target().distanceToSqr(camera)))
                .toList();
        renderState = new RenderState(visible);
    }

    public void submitLabels(LevelRenderContext context) {
        if (renderState.veins().isEmpty()) {
            return;
        }

        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poses = context.poseStack();
        for (OreVein vein : renderState.veins()) {
            Vec3 label = vein.labelPosition();
            poses.pushPose();
            poses.translate(label.x - camera.x, label.y - camera.y, label.z - camera.z);
            context.submitNodeCollector().submitNameTag(
                    poses, Vec3.ZERO, 0,
                    Component.literal(vein.type().displayName() + " x" + vein.blocks().size()),
                    true, FULL_BRIGHT, context.levelState().cameraRenderState);
            poses.popPose();
        }
    }

    public void render(LevelRenderContext context) {
        if (renderState.veins().isEmpty()) {
            return;
        }
        drawFilled(context);
        drawLines(context);
    }

    private void drawFilled(LevelRenderContext context) {
        StagedVertexBuffer.Draw draw = beginDraw(filledBuffer, FILLED_THROUGH_WALLS);
        PoseStack poses = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer vertices = filledBuffer.getVertexBuilder(draw);
        Matrix4fc matrix = poses.last().pose();
        int rendered = 0;

        outer:
        for (OreVein vein : renderState.veins()) {
            OreType type = vein.type();
            for (BlockPos pos : vein.blocks()) {
                if (rendered++ >= MAX_RENDERED_BLOCKS) {
                    break outer;
                }
                float x1 = pos.getX() + BOX_INSET;
                float y1 = pos.getY() + BOX_INSET;
                float z1 = pos.getZ() + BOX_INSET;
                float x2 = pos.getX() + 1F - BOX_INSET;
                float y2 = pos.getY() + 1F - BOX_INSET;
                float z2 = pos.getZ() + 1F - BOX_INSET;
                addBoxFaces(vertices, matrix, x1, y1, z1, x2, y2, z2,
                        type.red(), type.green(), type.blue(), 0.14F);
            }
        }
        poses.popPose();
        finishDraw(filledBuffer, draw, FILLED_THROUGH_WALLS);
    }

    private void drawLines(LevelRenderContext context) {
        StagedVertexBuffer.Draw draw = beginDraw(lineBuffer, LINES_THROUGH_WALLS);
        PoseStack poses = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer vertices = lineBuffer.getVertexBuilder(draw);
        Matrix4fc matrix = poses.last().pose();
        int rendered = 0;

        outer:
        for (OreVein vein : renderState.veins()) {
            OreType type = vein.type();
            for (BlockPos pos : vein.blocks()) {
                if (rendered++ >= MAX_RENDERED_BLOCKS) {
                    break outer;
                }
                float x1 = pos.getX() + BOX_INSET;
                float y1 = pos.getY() + BOX_INSET;
                float z1 = pos.getZ() + BOX_INSET;
                float x2 = pos.getX() + 1F - BOX_INSET;
                float y2 = pos.getY() + 1F - BOX_INSET;
                float z2 = pos.getZ() + 1F - BOX_INSET;
                addBoxLines(vertices, matrix, x1, y1, z1, x2, y2, z2,
                        type.red(), type.green(), type.blue(), 0.98F);
            }

            Vec3 direction = vein.target().subtract(camera);
            Vec3 start = direction.lengthSqr() > 0.001
                    ? camera.add(direction.normalize().scale(0.2)) : camera;
            addLine(vertices, matrix, start, vein.target(),
                    type.red(), type.green(), type.blue(), 0.78F);
        }
        poses.popPose();
        finishDraw(lineBuffer, draw, LINES_THROUGH_WALLS);
    }

    private static StagedVertexBuffer.Draw beginDraw(StagedVertexBuffer buffer, RenderPipeline pipeline) {
        VertexFormat format = pipeline.getVertexFormatBinding(0);
        if (format == null) {
            throw new IllegalStateException("Ore Tracer pipeline has no vertex format");
        }
        PrimitiveTopology primitive = pipeline.getPrimitiveTopology();
        return buffer.appendDraw(format, primitive,
                primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);
    }

    private static void finishDraw(StagedVertexBuffer buffer, StagedVertexBuffer.Draw draw, RenderPipeline pipeline) {
        buffer.upload();
        StagedVertexBuffer.ExecuteInfo info = buffer.getExecuteInfo(draw);
        if (info != null) {
            execute(info, pipeline);
        }
        buffer.endFrame();
    }

    private static void execute(StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        Minecraft minecraft = Minecraft.getInstance();
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);
        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        GpuTextureView color = target.getColorTextureView();
        if (color == null) {
            return;
        }

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Ore Tracer rendering", color, Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    private void rebuildVeinsIfNeeded() {
        if (cachedVersion == scanner.version()) {
            return;
        }
        cachedVersion = scanner.version();
        Map<Long, OreType> ores = scanner.found();
        Set<Long> visited = new HashSet<>();
        List<OreVein> veins = new ArrayList<>();

        for (Map.Entry<Long, OreType> entry : ores.entrySet()) {
            if (!visited.add(entry.getKey())) {
                continue;
            }
            OreType type = entry.getValue();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            List<BlockPos> blocks = new ArrayList<>();
            queue.add(BlockPos.of(entry.getKey()));

            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                blocks.add(current);
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = current.relative(direction);
                    long packed = neighbor.asLong();
                    if (ores.get(packed) == type && visited.add(packed)) {
                        queue.addLast(neighbor);
                    }
                }
            }

            double x = blocks.stream().mapToDouble(pos -> pos.getX() + 0.5).average().orElse(0);
            double y = blocks.stream().mapToDouble(pos -> pos.getY() + 0.5).average().orElse(0);
            double z = blocks.stream().mapToDouble(pos -> pos.getZ() + 0.5).average().orElse(0);
            int topY = blocks.stream().mapToInt(BlockPos::getY).max().orElse(0);
            veins.add(new OreVein(type, List.copyOf(blocks), new Vec3(x, y, z),
                    new Vec3(x, topY + 1.45, z)));
        }
        cachedVeins = List.copyOf(veins);
    }

    private static void addBoxFaces(VertexConsumer b, Matrix4fc m, float x1, float y1, float z1,
                                    float x2, float y2, float z2, float r, float g, float blue, float a) {
        quad(b,m,x1,y1,z1,x2,y1,z1,x2,y2,z1,x1,y2,z1,r,g,blue,a);
        quad(b,m,x2,y1,z2,x1,y1,z2,x1,y2,z2,x2,y2,z2,r,g,blue,a);
        quad(b,m,x1,y1,z2,x1,y1,z1,x1,y2,z1,x1,y2,z2,r,g,blue,a);
        quad(b,m,x2,y1,z1,x2,y1,z2,x2,y2,z2,x2,y2,z1,r,g,blue,a);
        quad(b,m,x1,y1,z2,x2,y1,z2,x2,y1,z1,x1,y1,z1,r,g,blue,a);
        quad(b,m,x1,y2,z1,x2,y2,z1,x2,y2,z2,x1,y2,z2,r,g,blue,a);
    }

    private static void quad(VertexConsumer b, Matrix4fc m,
                             float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz,
                             float r,float g,float blue,float a) {
        vertex(b,m,ax,ay,az,r,g,blue,a); vertex(b,m,bx,by,bz,r,g,blue,a);
        vertex(b,m,cx,cy,cz,r,g,blue,a); vertex(b,m,dx,dy,dz,r,g,blue,a);
    }

    private static void addBoxLines(VertexConsumer b, Matrix4fc m, float x1,float y1,float z1,
                                    float x2,float y2,float z2,float r,float g,float blue,float a) {
        line(b,m,x1,y1,z1,x2,y1,z1,r,g,blue,a); line(b,m,x2,y1,z1,x2,y1,z2,r,g,blue,a);
        line(b,m,x2,y1,z2,x1,y1,z2,r,g,blue,a); line(b,m,x1,y1,z2,x1,y1,z1,r,g,blue,a);
        line(b,m,x1,y2,z1,x2,y2,z1,r,g,blue,a); line(b,m,x2,y2,z1,x2,y2,z2,r,g,blue,a);
        line(b,m,x2,y2,z2,x1,y2,z2,r,g,blue,a); line(b,m,x1,y2,z2,x1,y2,z1,r,g,blue,a);
        line(b,m,x1,y1,z1,x1,y2,z1,r,g,blue,a); line(b,m,x2,y1,z1,x2,y2,z1,r,g,blue,a);
        line(b,m,x2,y1,z2,x2,y2,z2,r,g,blue,a); line(b,m,x1,y1,z2,x1,y2,z2,r,g,blue,a);
    }

    private static void addLine(VertexConsumer b, Matrix4fc m, Vec3 from, Vec3 to,
                                float r,float g,float blue,float a) {
        line(b,m,(float)from.x,(float)from.y,(float)from.z,
                (float)to.x,(float)to.y,(float)to.z,r,g,blue,a);
    }

    private static void line(VertexConsumer b, Matrix4fc m, float x1,float y1,float z1,
                             float x2,float y2,float z2,float r,float g,float blue,float a) {
        vertex(b,m,x1,y1,z1,r,g,blue,a); vertex(b,m,x2,y2,z2,r,g,blue,a);
    }

    private static void vertex(VertexConsumer b, Matrix4fc m, float x,float y,float z,
                               float r,float g,float blue,float a) {
        b.addVertex(m, x, y, z).setColor(r, g, blue, a);
    }

    @Override
    public void close() {
        filledBuffer.close();
        lineBuffer.close();
    }

    private record RenderState(List<OreVein> veins) {
    }
}
