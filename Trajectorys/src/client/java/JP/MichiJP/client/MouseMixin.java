package JP.MichiJP.mixin.client;

import JP.MichiJP.client.TrajectorysClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (TrajectorysClient.zoomKey != null && TrajectorysClient.zoomKey.isPressed()) {
            TrajectorysClient.onScroll(vertical);
            ci.cancel(); // ズーム時はホットバー切り替え等を無効化
        }
    }
}