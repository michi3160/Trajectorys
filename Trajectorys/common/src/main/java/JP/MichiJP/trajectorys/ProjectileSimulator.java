package JP.MichiJP.trajectorys;

import JP.MichiJP.trajectorys.mixin.CrossbowItemAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnReason;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class ProjectileSimulator {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

    public final Vector3d pos = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    private Vec3 prevPos3d = new Vec3(0, 0, 0);

    private Entity simulatingEntity;
    private Entity shooter;
    private double gravity;
    private double airDrag, waterDrag;
    private float width, height;

    public boolean set(LivingEntity user, ItemStack itemStack, double offset, boolean accurate, float tickDelta) {
        this.shooter = user;
        Item item = itemStack.getItem();

        if (item instanceof BowItem) {
            double charge = BowItem.getPowerForTime(user.getUseItemRemainingTicks()); // getPullProgress -> getPowerForTime
            if (charge <= 0.1) return false;
            set(user, 0, charge * 3, offset, 0.05, 0.6, accurate, tickDelta, EntityType.ARROW);
        }
        else if (item instanceof CrossbowItem) {
            ChargedProjectiles projectiles = itemStack.get(DataComponents.CHARGED_PROJECTILES);
            if (projectiles == null || projectiles.isEmpty()) return false;

            // Accessor経由で発射パワーを取得 (Mixin側もMojang名に合わせる必要あり)
            float speed = CrossbowItemAccessor.getSpeed(projectiles);
            if (projectiles.contains(Items.FIREWORK_ROCKET)) {
                set(user, 0, speed, offset, 0, 0.6, accurate, tickDelta, EntityType.FIREWORK_ROCKET);
            } else {
                set(user, 0, speed, offset, 0.05, 0.6, accurate, tickDelta, EntityType.ARROW);
            }
        }
        else if (item instanceof FishingRodItem) {
            setFishingBobber(user, tickDelta);
        }
        else if (item instanceof TridentItem) {
            set(user, 0, 2.5, offset, 0.05, 0.99, accurate, tickDelta, EntityType.TRIDENT);
        }
        else if (item instanceof SnowballItem) {
            set(user, 0, 1.5, offset, 0.03, 0.8, accurate, tickDelta, EntityType.SNOWBALL);
        }
        else if (item instanceof EggItem) {
            set(user, 0, 1.5, offset, 0.03, 0.8, accurate, tickDelta, EntityType.EGG);
        }
        else if (item instanceof EnderpearlItem) {
            set(user, 0, 1.5, offset, 0.03, 0.8, accurate, tickDelta, EntityType.ENDER_PEARL);
        }
        else if (item instanceof ExperienceBottleItem) {
            set(user, -20, 0.7, offset, 0.07, 0.8, accurate, tickDelta, EntityType.EXPERIENCE_BOTTLE);
        }
        else if (item instanceof SplashPotionItem) {
            set(user, -20, 0.5, offset, 0.05, 0.8, accurate, tickDelta, EntityType.POTION); // EntityType.SPLASH_POTION -> POTION
        }
        else if (item instanceof WindChargeItem) {
            set(user, 0, 1.5, offset, 0, 1.0, accurate, tickDelta, EntityType.WIND_CHARGE);
            this.airDrag = 1.0;
        }
        else {
            return false;
        }
        return true;
    }

    public void set(LivingEntity user, double roll, double speed, double offset, double gravity, double waterDrag, boolean accurate, float tickDelta, EntityType<?> type) {
        this.shooter = user;
        Vec3 renderPos = user.getPosition(tickDelta).add(0, user.getEyeHeight(user.getPose()), 0);
        pos.set(renderPos.x, renderPos.y, renderPos.z);

        float yaw = user.getViewYRot(tickDelta);
        float pitch = user.getViewXRot(tickDelta);

        double x, y, z;

        if (offset == 0) {
            x = -Math.sin(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);
            y = -Math.sin((pitch + roll) * 0.017453292);
            z = Math.cos(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);
        } else {
            // getOppositeRotationVectorは標準APIに存在しないため、UpVector(上方向)を軸として計算するか、
            // 垂直なベクトルを計算して代用します。ここでは一般的な軸回転のロジックを使用します。
            Vec3 vec3d = Vec3.directionFromRotation(pitch - 90, yaw); // 上方向ベクトルを近似
            Quaterniond quaternion = new Quaterniond().setAngleAxis(offset, vec3d.x, vec3d.y, vec3d.z);
            Vec3 vec3d2 = user.getViewVector(1.0F); // getRotationVec -> getViewVector
            Vector3d vector3f = new Vector3d(vec3d2.x, vec3d2.y, vec3d2.z);
            vector3f.rotate(quaternion);

            x = vector3f.x;
            y = vector3f.y;
            z = vector3f.z;
        }

        velocity.set(x, y, z).normalize().mul(speed);

        if (accurate) {
            Vec3 vel = user.getDeltaMovement(); // getVelocity -> getDeltaMovement
            velocity.add(vel.x, user.onGround() ? 0.0D : vel.y, vel.z);
        }

        this.simulatingEntity = type.create(mc.level, SpawnReason.LOAD);
        this.gravity = gravity;
        this.airDrag = 0.99;
        this.waterDrag = waterDrag;
        this.width = type.getWidth();
        this.height = type.getHeight();

        this.prevPos3d = new Vec3(pos.x, pos.y, pos.z);
    }

    public void setFishingBobber(LivingEntity user, float tickDelta) {
        this.shooter = user;
        float yaw = user.getViewYRot(tickDelta);
        float pitch = user.getViewXRot(tickDelta);

        double h = Math.cos(-yaw * 0.017453292F - 3.1415927F);
        double i = Math.sin(-yaw * 0.017453292F - 3.1415927F);
        double j = -Math.cos(-pitch * 0.017453292F);
        double k = Math.sin(-pitch * 0.017453292F);

        Vec3 renderPos = user.getPosition(tickDelta).add(0, user.getEyeHeight(user.getPose()), 0);
        pos.set(renderPos.x, renderPos.y, renderPos.z);
        pos.sub(i * 0.3, 0, h * 0.3);

        velocity.set(-i, Mth.clamp(-(k / j), -5, 5), -h);
        double l = velocity.length();
        velocity.mul(0.6 / l + 0.5, 0.6 / l + 0.5, 0.6 / l + 0.5);

        Vec3 userVel = user.getDeltaMovement();
        velocity.add(userVel.x, user.onGround() ? 0 : userVel.y, userVel.z);

        simulatingEntity = EntityType.FISHING_BOBBER.create(mc.level, SpawnReason.LOAD);
        gravity = 0.03;
        airDrag = 0.92;
        waterDrag = 0;
        width = EntityType.FISHING_BOBBER.getWidth();
        height = EntityType.FISHING_BOBBER.getHeight();

        this.prevPos3d = new Vec3(pos.x, pos.y, pos.z);
    }

    public boolean set(Entity entity, float tickDelta) {
        this.shooter = null;
        boolean accurate = false;
        if (entity instanceof AbstractArrow) setEntity(entity, 0.05, 0.6, accurate); // ArrowEntity -> AbstractArrow
        else if (entity instanceof ThrownTrident) setEntity(entity, 0.05, 0.99, accurate);
        else if (entity instanceof Enderpearl || entity instanceof Snowball || entity instanceof ThrownEgg) setEntity(entity, 0.03, 0.8, accurate);
        else if (entity instanceof ThrownExperienceBottle) setEntity(entity, 0.07, 0.8, accurate);
        else if (entity instanceof ThrownPotion) setEntity(entity, 0.05, 0.8, accurate);
        else if (entity instanceof LlamaSpit) setEntity(entity, 0.06, 0.99, accurate);
        else if (entity instanceof PrimedTnt) setEntity(entity, 0.04, 0.98, accurate);
        else if (entity instanceof FallingBlockEntity) setEntity(entity, 0.04, 0.98, accurate);
        else if (entity instanceof WitherSkull || entity instanceof Fireball || entity instanceof DragonFireball || entity instanceof WindCharge || entity instanceof SmallFireball) {
            setEntity(entity, 0, 1.0, accurate);
            this.airDrag = 1.0;
        } else {
            return false;
        }
        if (entity.isNoGravity()) this.gravity = 0;
        return true;
    }

    private void setEntity(Entity entity, double gravity, double waterDrag, boolean accurate) {
        pos.set(entity.getX(), entity.getY(), entity.getZ());
        double speed = entity.getDeltaMovement().length();
        if (speed > 0) {
            velocity.set(entity.getDeltaMovement().x, entity.getDeltaMovement().y, entity.getDeltaMovement().z).normalize().mul(speed);
        } else {
            velocity.set(0, 0, 0);
        }

        this.simulatingEntity = entity;
        this.gravity = gravity;
        this.airDrag = 0.99;
        this.waterDrag = waterDrag;
        this.width = entity.getBbWidth();
        this.height = entity.getBbHeight();
        this.prevPos3d = new Vec3(pos.x, pos.y, pos.z);

        if (entity instanceof Projectile projectile) {
            this.shooter = projectile.getOwner();
        }
    }

    public HitResult tick() {
        prevPos3d = new Vec3(pos.x, pos.y, pos.z);
        pos.add(velocity);

        velocity.mul(isTouchingWater() ? waterDrag : airDrag);
        velocity.sub(0, gravity, 0);

        Vec3 currentPos3d = new Vec3(pos.x, pos.y, pos.z);
        HitResult hitResult = mc.level.clip(new ClipContext(prevPos3d, currentPos3d, ClipContext.Block.COLLIDER, waterDrag == 0 ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, simulatingEntity));

        if (hitResult.getType() != HitResult.Type.MISS) {
            pos.set(hitResult.getLocation().x, hitResult.getLocation().y, hitResult.getLocation().z);
            currentPos3d = hitResult.getLocation();
        }

        AABB box = new AABB(prevPos3d.x - (width / 2f), prevPos3d.y, prevPos3d.z - (width / 2f), prevPos3d.x + (width / 2f), prevPos3d.y + height, prevPos3d.z + (width / 2f))
                .expandTowards(velocity.x, velocity.y, velocity.z).inflate(1.0D);

        HitResult hitResult2 = ProjectileUtil.getEntityHitResult(
                mc.level, simulatingEntity, prevPos3d, currentPos3d, box,
                entity -> !entity.isSpectator() && entity.isPickable() && entity != shooter,
                0.3f
        );

        if (hitResult2 != null) {
            return hitResult2;
        }

        return hitResult.getType() == HitResult.Type.MISS ? null : hitResult;
    }

    private boolean isTouchingWater() {
        blockPos.set(pos.x, pos.y, pos.z);
        FluidState fluidState = mc.level.getFluidState(blockPos);
        return (fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER));
    }
}