package JP.MichiJP.client;

import JP.MichiJP.config.TrajectorysConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity; // 追加
import net.minecraft.entity.TntEntity; // 追加
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TrajectorysClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("trajectorys");
    private static final ProjectileSimulator simulator = new ProjectileSimulator();

    private static final List<Path> paths = new ArrayList<>();

    private static int logThrottle = 0;
    private static final double MULTISHOT_OFFSET = Math.toRadians(10);
    private static Entity lastSoundEntity = null;

    @Override
    public void onInitializeClient() {
        TrajectorysConfig.load();
        LOGGER.info("[Trajectorys] Client Initialized.");
    }

    public static void onRenderWorld(MatrixStack matrices, float tickDelta, Camera camera, Matrix4f viewMatrix) {
        if (!TrajectorysConfig.get().enableMod) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d cameraPos = camera.getPos();
        paths.clear();

        // プレイヤーの計算
        for (PlayerEntity player : mc.world.getPlayers()) {
            boolean isSelf = (player == mc.player);
            if (isSelf && !TrajectorysConfig.get().showSelf) continue;
            if (!isSelf && !TrajectorysConfig.get().showOthers) continue;

            calculatePath(player, tickDelta);
        }

        // 発射体の計算
        if (TrajectorysConfig.get().showProjectiles) {
            for (Entity entity : mc.world.getEntities()) {
                // 変更: ProjectileEntity だけでなく、TNTや落下ブロックも対象にする
                if (entity instanceof ProjectileEntity || entity instanceof TntEntity || entity instanceof FallingBlockEntity) {
                    calculateFiredPath(entity, tickDelta);
                }
            }
        }

        // 描画
        renderPaths(matrices, cameraPos);

        // 音の再生
        playHitSound(mc);
    }

    private static void calculatePath(PlayerEntity player, float tickDelta) {
        ItemStack itemStack = player.getMainHandStack();
        if (!isProjectileWeapon(itemStack.getItem())) {
            itemStack = player.getOffHandStack();
            if (!isProjectileWeapon(itemStack.getItem())) return;
        }

        if (simulator.set(player, itemStack, 0, true, tickDelta)) {
            Path path = new Path();
            path.calculate();
            paths.add(path);

            if (itemStack.getItem() instanceof CrossbowItem && hasMultishot(itemStack, player)) {
                if (simulator.set(player, itemStack, MULTISHOT_OFFSET, true, tickDelta)) {
                    Path leftPath = new Path();
                    leftPath.calculate();
                    paths.add(leftPath);
                }
                if (simulator.set(player, itemStack, -MULTISHOT_OFFSET, true, tickDelta)) {
                    Path rightPath = new Path();
                    rightPath.calculate();
                    paths.add(rightPath);
                }
            }
        }
    }

    private static boolean hasMultishot(ItemStack stack, PlayerEntity player) {
        if (player.getEntityWorld() == null) return false;

        Optional<Registry<Enchantment>> registry = player.getEntityWorld().getRegistryManager()
                .getOptional(RegistryKeys.ENCHANTMENT);

        if (registry.isPresent()) {
            Optional<RegistryEntry.Reference<Enchantment>> entry = registry.get().getOptional(Enchantments.MULTISHOT);
            return entry.map(enchantmentReference -> EnchantmentHelper.getLevel(enchantmentReference, stack) > 0).orElse(false);
        }
        return false;
    }

    private static boolean isProjectileWeapon(Item item) {
        return item instanceof RangedWeaponItem || item instanceof FishingRodItem || item instanceof TridentItem ||
                item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderPearlItem ||
                item instanceof ExperienceBottleItem || item instanceof ThrowablePotionItem || item instanceof WindChargeItem;
    }

    private static void calculateFiredPath(Entity entity, float tickDelta) {
        if (simulator.set(entity, tickDelta)) {
            Path path = new Path();
            path.calculate();
            paths.add(path);
        }
    }

    private static void renderPaths(MatrixStack matrices, Vec3d cameraPos) {
        if (paths.isEmpty()) return;

        TrajectorysConfig config = TrajectorysConfig.get();

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // ImmediateモードのVertexConsumerProviderを作成
        BufferAllocator allocator = new BufferAllocator(256);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 線の色
        int r = (config.lineColor >> 16) & 0xFF;
        int g = (config.lineColor >> 8) & 0xFF;
        int b = config.lineColor & 0xFF;
        int a = 255;

        // ヒットボックスの色
        int hitR = (config.entityHitColor >> 16) & 0xFF;
        int hitG = (config.entityHitColor >> 8) & 0xFF;
        int hitB = config.entityHitColor & 0xFF;

        // パスごとにループして描画
        for (Path path : paths) {
            // 1. 線の描画
            if (config.renderLine) {
                RenderSystem.lineWidth((float) config.lineWidth);
                VertexConsumer lineBuffer = immediate.getBuffer(RenderLayer.getDebugLineStrip(config.lineWidth > 0 ? config.lineWidth : 2.0));

                for (int i = 0; i < path.points.size() - 1; i++) {
                    Vector3d p1 = path.points.get(i);
                    Vector3d p2 = path.points.get(i+1);

                    lineBuffer.vertex(matrix, (float)p1.x, (float)p1.y, (float)p1.z).color(r, g, b, a).normal(0, 1, 0);
                    lineBuffer.vertex(matrix, (float)p2.x, (float)p2.y, (float)p2.z).color(r, g, b, a).normal(0, 1, 0);
                }
            }

            // 2. ヒットボックスの描画
            if (config.renderHitBox && path.hitResult != null) {
                RenderSystem.lineWidth((float) config.hitBoxLineWidth);
                VertexConsumer boxBuffer = immediate.getBuffer(RenderLayer.getLines());

                if (path.hitResult.getType() == HitResult.Type.ENTITY) {
                    Entity entity = ((EntityHitResult) path.hitResult).getEntity();
                    Box box = entity.getBoundingBox();
                    drawBox(boxBuffer, matrix, box, hitR, hitG, hitB, a);
                } else if (path.hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) path.hitResult;
                    Vec3d hitPos = blockHit.getPos();
                    double x = hitPos.x;
                    double y = hitPos.y;
                    double z = hitPos.z;
                    double s = config.boxSize > 0 ? config.boxSize : 0.1;
                    drawBox(boxBuffer, matrix, new Box(x - s, y - s, z - s, x + s, y + s, z + s), config.blockHitColor >> 16 & 0xFF, config.blockHitColor >> 8 & 0xFF, config.blockHitColor & 0xFF, a);
                }
            }

            immediate.draw();
        }

        allocator.close();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderSystem.lineWidth(1.0f);
        matrices.pop();
    }

    private static void drawBox(VertexConsumer buffer, Matrix4f matrix, Box box, int r, int g, int b, int a) {
        float minX = (float)box.minX; float minY = (float)box.minY; float minZ = (float)box.minZ;
        float maxX = (float)box.maxX; float maxY = (float)box.maxY; float maxZ = (float)box.maxZ;

        drawLine(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        drawLine(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        drawLine(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    private static void drawLine(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(0, 1, 0);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(0, 1, 0);
    }

    private static void playHitSound(MinecraftClient mc) {
        if (!TrajectorysConfig.get().playSound) return;
        if (!paths.isEmpty()) {
            HitResult hit = paths.get(0).hitResult;
            if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
                Entity entity = ((EntityHitResult) hit).getEntity();
                if (entity != null && !entity.equals(lastSoundEntity)) {
                    mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                    lastSoundEntity = entity;
                    return;
                }
                lastSoundEntity = entity;
                return;
            }
        }
        lastSoundEntity = null;
    }

    private static class Path {
        public final List<Vector3d> points = new ArrayList<>();
        public HitResult hitResult = null;

        public void calculate() {
            points.add(new Vector3d(simulator.pos));
            for (int i = 0; i < 500; i++) {
                HitResult result = simulator.tick();
                if (result != null) {
                    hitResult = result;
                    points.add(new Vector3d(simulator.pos));
                    break;
                }
                points.add(new Vector3d(simulator.pos));
            }
        }
    }
}