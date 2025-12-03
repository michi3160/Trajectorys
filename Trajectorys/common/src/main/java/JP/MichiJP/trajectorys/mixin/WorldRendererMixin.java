package JP.MichiJP.trajectorys.mixin;

import JP.MichiJP.trajectorys.TrajectorysClientCommon;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onRender(CallbackInfo ci) { // 引数等はバージョンに合わせて調整
        // カメラなどの取得ロジック
        // TrajectorysClientCommon.onRenderWorld(...);
    }
}