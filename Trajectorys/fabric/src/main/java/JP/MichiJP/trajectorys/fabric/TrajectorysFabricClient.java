package JP.MichiJP.trajectorys.fabric;

import JP.MichiJP.trajectorys.TrajectorysClientCommon;
import net.fabricmc.api.ClientModInitializer;

public class TrajectorysFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TrajectorysClientCommon.init();
    }
}