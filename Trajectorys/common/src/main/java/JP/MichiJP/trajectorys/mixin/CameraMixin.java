package JP.MichiJP.trajectorys.mixin;

import JP.MichiJP.trajectorys.TrajectorysClientCommon;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(double x, double y, double z);

    // Mojang Mapping 1.21.1: setup
    @Inject(method = "setup", at = @At("RETURN"))
    private void onSetup(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (TrajectorysClientCommon.isCameraOverridden) {
            this.setPosition(
                    TrajectorysClientCommon.currentCameraPos.x,
                    TrajectorysClientCommon.currentCameraPos.y,
                    TrajectorysClientCommon.currentCameraPos.z
            );
            this.setRotation(
                    TrajectorysClientCommon.currentCameraYaw,
                    TrajectorysClientCommon.currentCameraPitch
            );
        }
    }
}