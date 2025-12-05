package JP.MichiJP.mixin.client;

import JP.MichiJP.client.TrajectorysClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("trajectorys-mixin");
    private int logThrottle = 0;

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) return;
        Camera camera = client.gameRenderer.getCamera();

        MatrixStack matrices = new MatrixStack();

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

        float tickDelta = 1.0f;

        TrajectorysClient.onRenderWorld(matrices, tickDelta, camera, null);
    }
}