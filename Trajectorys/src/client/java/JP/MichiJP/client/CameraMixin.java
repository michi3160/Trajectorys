package JP.MichiJP.mixin.client;

import JP.MichiJP.client.TrajectorysClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("RETURN"))
    private void onUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (TrajectorysClient.isCameraOverridden) {
            this.setPos(
                    TrajectorysClient.currentCameraPos.x,
                    TrajectorysClient.currentCameraPos.y,
                    TrajectorysClient.currentCameraPos.z
            );
            this.setRotation(
                    TrajectorysClient.currentCameraYaw,
                    TrajectorysClient.currentCameraPitch
            );
        }
    }
}