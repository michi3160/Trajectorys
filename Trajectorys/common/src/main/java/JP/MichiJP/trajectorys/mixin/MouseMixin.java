package JP.MichiJP.trajectorys.mixin;

import JP.MichiJP.trajectorys.TrajectorysClientCommon;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (TrajectorysClientCommon.zoomKey != null && TrajectorysClientCommon.zoomKey.isDown()) {
            TrajectorysClientCommon.onScroll(vertical);
            ci.cancel();
        }
    }
}