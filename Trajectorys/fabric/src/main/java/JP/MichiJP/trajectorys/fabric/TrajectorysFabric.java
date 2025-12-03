package JP.MichiJP.trajectorys.fabric;

import net.fabricmc.api.ModInitializer;

public class TrajectorysFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Commonモジュールの初期化があればここで呼び出します
        // 今回はClient主体のModなので、サーバーサイドで必須の初期化がなければ空でも動作します
    }
}