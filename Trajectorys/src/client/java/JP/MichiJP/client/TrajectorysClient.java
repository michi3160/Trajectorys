package JP.MichiJP.client;

import JP.MichiJP.config.TrajectorysConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.*;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TrajectorysClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("trajectorys");
    private static final ProjectileSimulator simulator = new ProjectileSimulator();

    public static final List<Path> paths = new ArrayList<>();

    // カメラ制御用
    public static KeyBinding zoomKey;
    public static boolean isCameraOverridden = false;
    public static Vector3d currentCameraPos = new Vector3d();
    public static float currentCameraYaw = 0f;
    public static float currentCameraPitch = 0f;

    // 追尾設定
    private static Entity trackingEntity = null;
    private static double cameraDistance = 5.0; // 初期値

    private static int logThrottle = 0;
    private static final double MULTISHOT_OFFSET = Math.toRadians(10);
    private static Entity lastSoundEntity = null;

    @Override
    public void onInitializeClient() {
        TrajectorysConfig.load();

        // 起動時に設定値から距離を読み込む
        cameraDistance = TrajectorysConfig.get().zoomDefaultDistance;

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("trajectorys", "general"));

        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.trajectorys.zoom",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) return;
            if (!TrajectorysConfig.get().enableZoom) return; // ズーム無効なら何もしない

            // Zキーを離したらリセット
            if (!zoomKey.isPressed()) {
                resetCamera();
                return;
            }

            if (trackingEntity == null) {
                Entity found = findNewProjectile(mc);
                if (found != null) {
                    trackingEntity = found;
                }
            }

            if (trackingEntity != null && !trackingEntity.isAlive()) {
                trackingEntity = null;
            }
        });

        LOGGER.info("[Trajectorys] Client Initialized.");
    }

    // スクロール処理 (Mixinから呼び出し)
    public static void onScroll(double amount) {
        TrajectorysConfig config = TrajectorysConfig.get();
        if (config.enableZoom && zoomKey.isPressed()) {
            // 設定された感度を使用
            cameraDistance -= amount * config.zoomScrollSensitivity;
            if (cameraDistance < 1.0) cameraDistance = 1.0;
            if (cameraDistance > 50.0) cameraDistance = 50.0;
        }
    }

    private void resetCamera() {
        isCameraOverridden = false;
        trackingEntity = null;
        // リセット時に設定のデフォルト距離に戻す
        cameraDistance = TrajectorysConfig.get().zoomDefaultDistance;
    }

    private Entity findNewProjectile(MinecraftClient mc) {
        Iterable<Entity> entities = mc.world.getEntities();
        Entity bestCandidate = null;
        double minDistanceSq = 100.0;

        for (Entity entity : entities) {
            if (entity == mc.player) continue;
            if (entity.age > 20) continue;

            double distSq = entity.squaredDistanceTo(mc.player);
            if (distSq > minDistanceSq) continue;

            boolean isMine = false;
            if (entity instanceof ProjectileEntity proj && proj.getOwner() == mc.player) isMine = true;
            else if (entity instanceof TntEntity tnt && tnt.getOwner() == mc.player) isMine = true;
            else if (entity instanceof FallingBlockEntity && distSq < 9.0) isMine = true;

            if (isMine) {
                minDistanceSq = distSq;
                bestCandidate = entity;
            }
        }
        return bestCandidate;
    }

    public static void onRenderWorld(MatrixStack matrices, float tickDelta, Camera camera, Matrix4f viewMatrix) {
        TrajectorysConfig config = TrajectorysConfig.get();
        if (!config.enableMod) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        paths.clear();
        Path playerPreviewPath = null;

        for (PlayerEntity player : mc.world.getPlayers()) {
            boolean isSelf = (player == mc.player);
            if (isSelf && !config.showSelf) continue;
            if (!isSelf && !config.showOthers) continue;

            Path p = calculatePath(player, tickDelta);
            if (isSelf) playerPreviewPath = p;
        }

        if (config.showProjectiles) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof ProjectileEntity || entity instanceof TntEntity || entity instanceof FallingBlockEntity) {
                    calculateFiredPath(entity, tickDelta);
                }
            }
        }

        // --- カメラ位置の制御 ---
        if (config.enableZoom && zoomKey.isPressed()) {
            isCameraOverridden = true;

            if (trackingEntity != null) {
                updateCameraToFollowEntity(trackingEntity, tickDelta);
            } else if (playerPreviewPath != null && !playerPreviewPath.points.isEmpty()) {
                updateCameraToPreview(playerPreviewPath);
            } else {
                isCameraOverridden = false;
            }
        } else {
            isCameraOverridden = false;
        }

        Vec3d renderCameraPos = camera.getPos();
        renderPaths(matrices, renderCameraPos);
        playHitSound(mc);
    }

    private static void updateCameraToFollowEntity(Entity entity, float tickDelta) {
        Vec3d pos = entity.getLerpedPos(tickDelta).add(0, entity.getHeight() * 0.5, 0);
        Vec3d velocity = entity.getVelocity();
        Vec3d viewDir = velocity.normalize();
        if (viewDir.lengthSquared() < 0.0001) viewDir = new Vec3d(0, -1, 0);

        Vec3d camPos = pos.subtract(viewDir.multiply(cameraDistance));
        currentCameraPos.set(camPos.x, camPos.y, camPos.z);
        lookAt(currentCameraPos, new Vector3d(pos.x, pos.y, pos.z));
    }

    private static void updateCameraToPreview(Path path) {
        List<Vector3d> points = path.points;
        if (points.isEmpty()) return;

        Vector3d impactPos = points.get(points.size() - 1);
        double currentDist = 0.0;
        Vector3d targetPos = new Vector3d(impactPos);

        for (int i = points.size() - 1; i > 0; i--) {
            Vector3d p1 = points.get(i);
            Vector3d p2 = points.get(i - 1);
            double segLen = p1.distance(p2);

            if (currentDist + segLen >= cameraDistance) {
                double remaining = cameraDistance - currentDist;
                double fraction = remaining / segLen;
                targetPos.x = MathHelper.lerp(fraction, p1.x, p2.x);
                targetPos.y = MathHelper.lerp(fraction, p1.y, p2.y);
                targetPos.z = MathHelper.lerp(fraction, p1.z, p2.z);
                break;
            }
            currentDist += segLen;
            targetPos.set(p2);
        }

        currentCameraPos.set(targetPos);
        lookAt(currentCameraPos, impactPos);
    }

    private static void lookAt(Vector3d from, Vector3d target) {
        double dx = target.x - from.x;
        double dy = target.y - from.y;
        double dz = target.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        currentCameraYaw = (float)(Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        currentCameraPitch = (float)-(Math.atan2(dy, dist) * 180.0D / Math.PI);
    }

    private static Path calculatePath(PlayerEntity player, float tickDelta) {
        ItemStack itemStack = player.getMainHandStack();
        if (!isProjectileWeapon(itemStack.getItem())) {
            itemStack = player.getOffHandStack();
            if (!isProjectileWeapon(itemStack.getItem())) return null;
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
            return path;
        }
        return null;
    }

    private static boolean hasMultishot(ItemStack stack, PlayerEntity player) {
        if (player.getEntityWorld() == null) return false;
        Optional<Registry<Enchantment>> registry = player.getEntityWorld().getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
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
        BufferAllocator allocator = new BufferAllocator(256);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int r = (config.lineColor >> 16) & 0xFF;
        int g = (config.lineColor >> 8) & 0xFF;
        int b = config.lineColor & 0xFF;
        int a = 255;
        int hitR = (config.entityHitColor >> 16) & 0xFF;
        int hitG = (config.entityHitColor >> 8) & 0xFF;
        int hitB = config.entityHitColor & 0xFF;

        for (Path path : paths) {
            if (config.renderLine) {
                // lineWidth設定は削除されたので固定値またはデフォルトを使用
                // RenderSystem.lineWidth((float) config.lineWidth); <- 削除
                VertexConsumer lineBuffer = immediate.getBuffer(RenderLayer.getDebugLineStrip(2.0));

                for (int i = 0; i < path.points.size() - 1; i++) {
                    Vector3d p1 = path.points.get(i);
                    Vector3d p2 = path.points.get(i+1);
                    lineBuffer.vertex(matrix, (float)p1.x, (float)p1.y, (float)p1.z).color(r, g, b, a).normal(0, 1, 0);
                    lineBuffer.vertex(matrix, (float)p2.x, (float)p2.y, (float)p2.z).color(r, g, b, a).normal(0, 1, 0);
                }
            }
            if (config.renderHitBox && path.hitResult != null) {
                // hitBoxLineWidth設定も削除
                VertexConsumer boxBuffer = immediate.getBuffer(RenderLayer.getLines());
                if (path.hitResult.getType() == HitResult.Type.ENTITY) {
                    Entity entity = ((EntityHitResult) path.hitResult).getEntity();
                    Box box = entity.getBoundingBox();
                    drawBox(boxBuffer, matrix, box, hitR, hitG, hitB, a);
                } else if (path.hitResult.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) path.hitResult;
                    Vec3d hitPos = blockHit.getPos();
                    double x = hitPos.x; double y = hitPos.y; double z = hitPos.z;
                    double s = config.boxSize > 0 ? config.boxSize : 0.1;
                    drawBox(boxBuffer, matrix, new Box(x - s, y - s, z - s, x + s, y + s, z + s), config.blockHitColor >> 16 & 0xFF, config.blockHitColor >> 8 & 0xFF, config.blockHitColor & 0xFF, a);
                }
            }
            immediate.draw();
        }
        allocator.close();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
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

    public static class Path {
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