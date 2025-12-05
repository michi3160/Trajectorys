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

    public boolean enableMod = true;
    public boolean showSelf = true;
    public boolean showOthers = true;
    public boolean showProjectiles = true;
    public boolean playSound = true;

    public boolean renderLine = true;
    public double lineWidth = 2.0;
    public int lineColor = 0xFF9600; // デフォルトRGB

    public boolean renderHitBox = true;
    public double hitBoxLineWidth = 2.0;
    public double boxSize = 0.1;
    public int entityHitColor = 0xFF0000; // デフォルトRed
    public int blockHitColor = 0x00FF00;  // デフォルトGreen

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