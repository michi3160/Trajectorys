package JP.MichiJP.trajectorys;

import JP.MichiJP.trajectorys.config.TrajectorysConfig;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class TrajectorysClientCommon {
    private static final ProjectileSimulator simulator = new ProjectileSimulator();
    public static final List<Path> paths = new ArrayList<>();

    public static KeyMapping zoomKey;
    public static boolean isCameraOverridden = false;
    public static Vector3d currentCameraPos = new Vector3d();
    public static float currentCameraYaw = 0f;
    public static float currentCameraPitch = 0f;

    private static Entity trackingEntity = null;
    private static double cameraDistance = 5.0;
    private static Entity lastSoundEntity = null;
    private static final double MULTISHOT_OFFSET = Math.toRadians(10);

    public static void init() {
        TrajectorysConfig.load();
        cameraDistance = TrajectorysConfig.get().zoomDefaultDistance;

        zoomKey = new KeyMapping(
                "key.trajectorys.zoom",
                GLFW.GLFW_KEY_Z,
                "key.category.trajectorys"
        );
        KeyMappingRegistry.register(zoomKey);

        ClientTickEvent.CLIENT_POST.register(mc -> {
            if (mc.player == null || mc.level == null) return;
            if (!TrajectorysConfig.get().enableZoom) return;

            if (!zoomKey.isDown()) {
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
    }

    public static void onScroll(double amount) {
        TrajectorysConfig config = TrajectorysConfig.get();
        if (config.enableZoom && zoomKey.isDown()) {
            cameraDistance -= amount * config.zoomScrollSensitivity;
            if (cameraDistance < 1.0) cameraDistance = 1.0;
            if (cameraDistance > 50.0) cameraDistance = 50.0;
        }
    }

    private static void resetCamera() {
        isCameraOverridden = false;
        trackingEntity = null;
        cameraDistance = TrajectorysConfig.get().zoomDefaultDistance;
    }

    private static Entity findNewProjectile(Minecraft mc) {
        Iterable<Entity> entities = mc.level.entitiesForRendering();
        Entity bestCandidate = null;
        double minDistanceSq = 100.0;

        for (Entity entity : entities) {
            if (entity == mc.player) continue;
            if (entity.tickCount > 20) continue;

            double distSq = entity.distanceToSqr(mc.player);
            if (distSq > minDistanceSq) continue;

            boolean isMine = false;
            if (entity instanceof Projectile proj && proj.getOwner() == mc.player) isMine = true;
            else if (entity instanceof PrimedTnt tnt && tnt.getOwner() == mc.player) isMine = true;
            else if (entity instanceof FallingBlockEntity && distSq < 9.0) isMine = true;

            if (isMine) {
                minDistanceSq = distSq;
                bestCandidate = entity;
            }
        }
        return bestCandidate;
    }

    public static void onRenderWorld(PoseStack matrices, float tickDelta, Camera camera) {
        TrajectorysConfig config = TrajectorysConfig.get();
        if (!config.enableMod) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        paths.clear();
        Path playerPreviewPath = null;

        for (Player player : mc.level.players()) {
            boolean isSelf = (player == mc.player);
            if (isSelf && !config.showSelf) continue;
            if (!isSelf && !config.showOthers) continue;

            Path p = calculatePath(player, tickDelta);
            if (isSelf) playerPreviewPath = p;
        }

        if (config.showProjectiles) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Projectile || entity instanceof PrimedTnt || entity instanceof FallingBlockEntity) {
                    calculateFiredPath(entity, tickDelta);
                }
            }
        }

        if (config.enableZoom && zoomKey.isDown()) {
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

        Vec3 renderCameraPos = camera.getPosition();
        renderPaths(matrices, renderCameraPos);
        playHitSound(mc);
    }

    private static void updateCameraToFollowEntity(Entity entity, float tickDelta) {
        Vec3 pos = entity.getPosition(tickDelta).add(0, entity.getBbHeight() * 0.5, 0);
        Vec3 velocity = entity.getDeltaMovement();
        Vec3 viewDir = velocity.normalize();
        if (viewDir.lengthSqr() < 0.0001) viewDir = new Vec3(0, -1, 0);

        Vec3 camPos = pos.subtract(viewDir.scale(cameraDistance));
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
                targetPos.x = net.minecraft.util.Mth.lerp(fraction, p1.x, p2.x);
                targetPos.y = net.minecraft.util.Mth.lerp(fraction, p1.y, p2.y);
                targetPos.z = net.minecraft.util.Mth.lerp(fraction, p1.z, p2.z);
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

    private static Path calculatePath(Player player, float tickDelta) {
        ItemStack itemStack = player.getMainHandItem();
        if (!isProjectileWeapon(itemStack.getItem())) {
            itemStack = player.getOffhandItem();
            if (!isProjectileWeapon(itemStack.getItem())) return null;
        }

        if (simulator.set(player, itemStack, 0, true, tickDelta)) {
            Path path = new Path();
            path.calculate();
            paths.add(path);
            
            // Multishot logic (simplified for Common)
             var registry = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
             var multishot = registry.getOrThrow(Enchantments.MULTISHOT);
             boolean hasMultishot = EnchantmentHelper.getItemEnchantmentLevel(multishot, itemStack) > 0;

            if (itemStack.getItem() instanceof CrossbowItem && hasMultishot) {
                if (simulator.set(player, itemStack, MULTISHOT_OFFSET, true, tickDelta)) {
                    Path left = new Path(); left.calculate(); paths.add(left);
                }
                if (simulator.set(player, itemStack, -MULTISHOT_OFFSET, true, tickDelta)) {
                    Path right = new Path(); right.calculate(); paths.add(right);
                }
            }
            return path;
        }
        return null;
    }

    private static boolean isProjectileWeapon(Item item) {
        return item instanceof ProjectileWeaponItem || item instanceof FishingRodItem || item instanceof TridentItem ||
                item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderpearlItem ||
                item instanceof ExperienceBottleItem || item instanceof SplashPotionItem || item instanceof WindChargeItem;
    }

    private static void calculateFiredPath(Entity entity, float tickDelta) {
        if (simulator.set(entity, tickDelta)) {
            Path path = new Path();
            path.calculate();
            paths.add(path);
        }
    }

    private static void renderPaths(PoseStack matrices, Vec3 cameraPos) {
        if (paths.isEmpty()) return;
        TrajectorysConfig config = TrajectorysConfig.get();
        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR_NORMAL);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorNormalShader);

        int r = (config.lineColor >> 16) & 0xFF;
        int g = (config.lineColor >> 8) & 0xFF;
        int b = config.lineColor & 0xFF;
        int a = 255;
        
        // Lines
        if (config.renderLine) {
            for (Path path : paths) {
                 // 新しいBufferBuilderの仕様に合わせてループ内で頂点を追加する形に変更推奨だが、
                 // ここでは簡略化して1つのバッファにまとめる想定 (実際はパスごとにdrawが必要な場合あり)
                 // Minecraft 1.21ではBufferBuilderの使い方が変わっているため注意
            }
             // 1.21対応の簡易描画ロジック (概念コード)
            for (Path path : paths) {
                // Line strip
                BufferBuilder lineBuf = tesselator.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                for (Vector3d p : path.points) {
                    lineBuf.addVertex((float)p.x, (float)p.y, (float)p.z).setColor(r, g, b, a).setNormal(0, 1, 0);
                }
                net.minecraft.client.renderer.BufferUploader.drawWithShader(lineBuf.buildOrThrow());
            }
        }
        
        // HitBoxes
        if (config.renderHitBox) {
            int hr = (config.entityHitColor >> 16) & 0xFF;
            int hg = (config.entityHitColor >> 8) & 0xFF;
            int hb = config.entityHitColor & 0xFF;

            for (Path path : paths) {
                 if (path.hitResult == null) continue;
                 BufferBuilder boxBuf = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                 
                 if (path.hitResult.getType() == HitResult.Type.ENTITY) {
                     Entity entity = ((EntityHitResult) path.hitResult).getEntity();
                     AABB box = entity.getBoundingBox();
                     drawBox(boxBuf, box, hr, hg, hb, a);
                 } else if (path.hitResult.getType() == HitResult.Type.BLOCK) {
                     Vec3 hitPos = path.hitResult.getLocation();
                     double s = config.boxSize > 0 ? config.boxSize : 0.1;
                     AABB box = new AABB(hitPos.x - s, hitPos.y - s, hitPos.z - s, hitPos.x + s, hitPos.y + s, hitPos.z + s);
                     int br = (config.blockHitColor >> 16) & 0xFF;
                     int bg = (config.blockHitColor >> 8) & 0xFF;
                     int bb = config.blockHitColor & 0xFF;
                     drawBox(boxBuf, box, br, bg, bb, a);
                 }
                 net.minecraft.client.renderer.BufferUploader.drawWithShader(boxBuf.buildOrThrow());
            }
        }

        RenderSystem.enableDepthTest();
        matrices.popPose();
    }
    
    // 描画ヘルパー
    private static void drawBox(BufferBuilder buffer, AABB box, int r, int g, int b, int a) {
        float x1 = (float)box.minX, y1 = (float)box.minY, z1 = (float)box.minZ;
        float x2 = (float)box.maxX, y2 = (float)box.maxY, z2 = (float)box.maxZ;
        drawLine(buffer, x1, y1, z1, x2, y1, z1, r, g, b, a);
        drawLine(buffer, x2, y1, z1, x2, y1, z2, r, g, b, a);
        drawLine(buffer, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawLine(buffer, x1, y1, z2, x1, y1, z1, r, g, b, a);
        drawLine(buffer, x1, y2, z1, x2, y2, z1, r, g, b, a);
        drawLine(buffer, x2, y2, z1, x2, y2, z2, r, g, b, a);
        drawLine(buffer, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawLine(buffer, x1, y2, z2, x1, y2, z1, r, g, b, a);
        drawLine(buffer, x1, y1, z1, x1, y2, z1, r, g, b, a);
        drawLine(buffer, x2, y1, z1, x2, y2, z1, r, g, b, a);
        drawLine(buffer, x2, y1, z2, x2, y2, z2, r, g, b, a);
        drawLine(buffer, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }
    
    private static void drawLine(BufferBuilder buffer, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
        buffer.addVertex(x1, y1, z1).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(x2, y2, z2).setColor(r, g, b, a).setNormal(0, 1, 0);
    }

    private static void playHitSound(Minecraft mc) {
        if (!TrajectorysConfig.get().playSound) return;
        if (!paths.isEmpty()) {
            HitResult hit = paths.get(0).hitResult;
            if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
                Entity entity = ((EntityHitResult) hit).getEntity();
                if (entity != null && !entity.equals(lastSoundEntity)) {
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
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