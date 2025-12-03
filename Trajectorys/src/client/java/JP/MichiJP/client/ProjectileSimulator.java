package JP.MichiJP.client;

import JP.MichiJP.mixin.CrossbowItemAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.projectile.thrown.*;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public class ProjectileSimulator {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final BlockPos.Mutable blockPos = new BlockPos.Mutable();

    public final Vector3d pos = new Vector3d();
    private final Vector3d velocity = new Vector3d();
    private Vec3d prevPos3d = new Vec3d(0, 0, 0);

    private Entity simulatingEntity;
    private Entity shooter;
    private double gravity;
    private double airDrag, waterDrag;
    private float width, height;

    public boolean set(LivingEntity user, ItemStack itemStack, double offset, boolean accurate, float tickDelta) {
        this.shooter = user;
        Item item = itemStack.getItem();

        if (item instanceof BowItem) {
            double charge = BowItem.getPullProgress(user.getItemUseTime());
            if (charge <= 0.1) return false;
            set(user, 0, charge * 3, offset, 0.05, 0.6, accurate, tickDelta, EntityType.ARROW);
        }
        else if (item instanceof CrossbowItem) {
            ChargedProjectilesComponent projectiles = itemStack.get(DataComponentTypes.CHARGED_PROJECTILES);
            if (projectiles == null || projectiles.isEmpty()) return false;

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
        else if (item instanceof EnderPearlItem) {
            set(user, 0, 1.5, offset, 0.03, 0.8, accurate, tickDelta, EntityType.ENDER_PEARL);
        }
        else if (item instanceof ExperienceBottleItem) {
            set(user, -20, 0.7, offset, 0.07, 0.8, accurate, tickDelta, EntityType.EXPERIENCE_BOTTLE);
        }
        else if (item instanceof ThrowablePotionItem) {
            set(user, -20, 0.5, offset, 0.05, 0.8, accurate, tickDelta, EntityType.SPLASH_POTION);
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
        Vec3d renderPos = user.getLerpedPos(tickDelta).add(0, user.getEyeHeight(user.getPose()), 0);
        pos.set(renderPos.x, renderPos.y, renderPos.z);

        float yaw = user.getYaw(tickDelta);
        float pitch = user.getPitch(tickDelta);

        double x, y, z;

        if (offset == 0) {
            x = -Math.sin(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);
            y = -Math.sin((pitch + roll) * 0.017453292);
            z = Math.cos(yaw * 0.017453292) * Math.cos(pitch * 0.017453292);
        } else {
            Vec3d vec3d = user.getOppositeRotationVector(1.0F);
            Quaterniond quaternion = new Quaterniond().setAngleAxis(offset, vec3d.x, vec3d.y, vec3d.z);
            Vec3d vec3d2 = user.getRotationVec(1.0F);
            Vector3d vector3f = new Vector3d(vec3d2.x, vec3d2.y, vec3d2.z);
            vector3f.rotate(quaternion);

            x = vector3f.x;
            y = vector3f.y;
            z = vector3f.z;
        }

        velocity.set(x, y, z).normalize().mul(speed);

        if (accurate) {
            Vec3d vel = user.getVelocity();
            velocity.add(vel.x, user.isOnGround() ? 0.0D : vel.y, vel.z);
        }

        this.simulatingEntity = type.create(mc.world, net.minecraft.entity.SpawnReason.LOAD);
        this.gravity = gravity;
        this.airDrag = 0.99;
        this.waterDrag = waterDrag;
        this.width = type.getWidth();
        this.height = type.getHeight();

        this.prevPos3d = new Vec3d(pos.x, pos.y, pos.z);
    }

    public void setFishingBobber(LivingEntity user, float tickDelta) {
        this.shooter = user;
        float yaw = user.getYaw(tickDelta);
        float pitch = user.getPitch(tickDelta);

        double h = Math.cos(-yaw * 0.017453292F - 3.1415927F);
        double i = Math.sin(-yaw * 0.017453292F - 3.1415927F);
        double j = -Math.cos(-pitch * 0.017453292F);
        double k = Math.sin(-pitch * 0.017453292F);

        Vec3d renderPos = user.getLerpedPos(tickDelta).add(0, user.getEyeHeight(user.getPose()), 0);
        pos.set(renderPos.x, renderPos.y, renderPos.z);
        pos.sub(i * 0.3, 0, h * 0.3);

        velocity.set(-i, MathHelper.clamp(-(k / j), -5, 5), -h);
        double l = velocity.length();
        velocity.mul(0.6 / l + 0.5, 0.6 / l + 0.5, 0.6 / l + 0.5);

        Vec3d userVel = user.getVelocity();
        velocity.add(userVel.x, user.isOnGround() ? 0 : userVel.y, userVel.z);

        simulatingEntity = EntityType.FISHING_BOBBER.create(mc.world, net.minecraft.entity.SpawnReason.LOAD);
        gravity = 0.03;
        airDrag = 0.92;
        waterDrag = 0;
        width = EntityType.FISHING_BOBBER.getWidth();
        height = EntityType.FISHING_BOBBER.getHeight();

        this.prevPos3d = new Vec3d(pos.x, pos.y, pos.z);
    }

    public boolean set(Entity entity, float tickDelta) {
        this.shooter = null;
        boolean accurate = false;
        if (entity instanceof ArrowEntity) setEntity(entity, 0.05, 0.6, accurate);
        else if (entity instanceof TridentEntity) setEntity(entity, 0.05, 0.99, accurate);
        else if (entity instanceof EnderPearlEntity || entity instanceof SnowballEntity || entity instanceof EggEntity) setEntity(entity, 0.03, 0.8, accurate);
        else if (entity instanceof ExperienceBottleEntity) setEntity(entity, 0.07, 0.8, accurate);
        else if (entity instanceof PotionEntity) setEntity(entity, 0.05, 0.8, accurate);
        else if (entity instanceof LlamaSpitEntity) setEntity(entity, 0.06, 0.99, accurate);
        else if (entity instanceof TntEntity) setEntity(entity, 0.04, 0.98, accurate);
        else if (entity instanceof FallingBlockEntity) setEntity(entity, 0.04, 0.98, accurate);
            // SmallFireballEntity (ブレイズの火の玉) を追加
        else if (entity instanceof WitherSkullEntity || entity instanceof FireballEntity || entity instanceof DragonFireballEntity || entity instanceof WindChargeEntity || entity instanceof SmallFireballEntity) {
            setEntity(entity, 0, 1.0, accurate);
            this.airDrag = 1.0;
        } else {
            return false;
        }
        if (entity.hasNoGravity()) this.gravity = 0;
        return true;
    }

    private void setEntity(Entity entity, double gravity, double waterDrag, boolean accurate) {
        pos.set(entity.getX(), entity.getY(), entity.getZ());
        double speed = entity.getVelocity().length();
        if (speed > 0) {
            velocity.set(entity.getVelocity().x, entity.getVelocity().y, entity.getVelocity().z).normalize().mul(speed);
        } else {
            velocity.set(0, 0, 0);
        }

        this.simulatingEntity = entity;
        this.gravity = gravity;
        this.airDrag = 0.99;
        this.waterDrag = waterDrag;
        this.width = entity.getWidth();
        this.height = entity.getHeight();
        this.prevPos3d = new Vec3d(pos.x, pos.y, pos.z);

        if (entity instanceof ProjectileEntity projectile) {
            this.shooter = projectile.getOwner();
        }
    }

    public HitResult tick() {
        prevPos3d = new Vec3d(pos.x, pos.y, pos.z);
        pos.add(velocity);

        velocity.mul(isTouchingWater() ? waterDrag : airDrag);
        velocity.sub(0, gravity, 0);

        Vec3d currentPos3d = new Vec3d(pos.x, pos.y, pos.z);
        HitResult hitResult = mc.world.raycast(new RaycastContext(prevPos3d, currentPos3d, RaycastContext.ShapeType.COLLIDER, waterDrag == 0 ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE, simulatingEntity));

        if (hitResult.getType() != HitResult.Type.MISS) {
            pos.set(hitResult.getPos().x, hitResult.getPos().y, hitResult.getPos().z);
            currentPos3d = hitResult.getPos();
        }

        Box box = new Box(prevPos3d.x - (width / 2f), prevPos3d.y, prevPos3d.z - (width / 2f), prevPos3d.x + (width / 2f), prevPos3d.y + height, prevPos3d.z + (width / 2f))
                .stretch(velocity.x, velocity.y, velocity.z).expand(1.0D);

        HitResult hitResult2 = ProjectileUtil.getEntityCollision(
                mc.world, simulatingEntity, prevPos3d, currentPos3d, box,
                entity -> !entity.isSpectator() && entity.canHit() && entity != shooter,
                0.3f
        );

        if (hitResult2 != null) {
            return hitResult2;
        }

        return hitResult.getType() == HitResult.Type.MISS ? null : hitResult;
    }

    private boolean isTouchingWater() {
        blockPos.set(pos.x, pos.y, pos.z);
        FluidState fluidState = mc.world.getFluidState(blockPos);
        return (fluidState.getFluid() == Fluids.WATER || fluidState.getFluid() == Fluids.FLOWING_WATER);
    }
}