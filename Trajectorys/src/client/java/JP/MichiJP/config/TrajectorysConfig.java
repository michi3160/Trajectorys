package JP.MichiJP.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TrajectorysConfig {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "trajectorys.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TrajectorysConfig instance;

    // 全般
    public boolean enableMod = true;
    public boolean showSelf = true;
    public boolean showOthers = true;
    public boolean showProjectiles = true;
    public boolean playSound = true;

    // 描画設定 (線の太さは機能しないため削除)
    public boolean renderLine = true;
    public int lineColor = 0xFF9600;

    public boolean renderHitBox = true;
    public double boxSize = 0.1;
    public int entityHitColor = 0xFF0000;
    public int blockHitColor = 0x00FF00;

    // ズーム設定 (新規追加)
    public boolean enableZoom = true;
    public double zoomDefaultDistance = 5.0; // デフォルト距離
    public double zoomScrollSensitivity = 0.2; // スクロール感度 (スピード)

    public static TrajectorysConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, TrajectorysConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (instance == null) {
            instance = new TrajectorysConfig();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}