package JP.MichiJP.trajectorys.neoforge;

import JP.MichiJP.trajectorys.TrajectorysClientCommon;
import JP.MichiJP.trajectorys.config.TrajectorysConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModLoadingContext;

@Mod("trajectorys")
public class TrajectorysNeoForge {
    public TrajectorysNeoForge() {
        if (FMLEnvironment.dist.isClient()) {
            TrajectorysClientCommon.init();
            
            // Config Screen登録
            ModLoadingContext.get().registerExtensionPoint(
                IConfigScreenFactory.class,
                () -> (client, parent) -> TrajectorysConfig.getModConfigScreenFactory().create(parent)
            );
        }
    }
}