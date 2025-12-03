package JP.MichiJP.integration;

import JP.MichiJP.config.TrajectorysConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            TrajectorysConfig config = TrajectorysConfig.get();
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("title.trajectorys.config"));

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // カテゴリ
            ConfigCategory general = builder.getOrCreateCategory(Text.translatable("category.trajectorys.general"));
            ConfigCategory zoom = builder.getOrCreateCategory(Text.translatable("category.trajectorys.zoom"));
            ConfigCategory render = builder.getOrCreateCategory(Text.translatable("category.trajectorys.render"));

            // --- General ---
            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.trajectorys.enableMod"), config.enableMod)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> config.enableMod = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.trajectorys.showSelf"), config.showSelf)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> config.showSelf = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.trajectorys.showOthers"), config.showOthers)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> config.showOthers = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.trajectorys.showProjectiles"), config.showProjectiles)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> config.showProjectiles = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.trajectorys.playSound"), config.playSound)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> config.playSound = newValue)
                    .build());

            // --- Zoom Settings ---
            // 修正: ツールチップを Text.translatable で指定
            zoom.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.trajectorys.enableZoom"), config.enableZoom)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("tooltip.trajectorys.enableZoom"))
                    .setSaveConsumer(newValue -> config.enableZoom = newValue)
                    .build());

            zoom.addEntry(entryBuilder.startDoubleField(Text.translatable("option.trajectorys.zoomDefaultDistance"), config.zoomDefaultDistance)
                    .setDefaultValue(5.0)
                    .setMin(1.0)
                    .setMax(30.0)
                    .setTooltip(Text.translatable("tooltip.trajectorys.zoomDefaultDistance"))
                    .setSaveConsumer(newValue -> config.zoomDefaultDistance = newValue)
                    .build());

            zoom.addEntry(entryBuilder.startDoubleField(Text.translatable("option.trajectorys.zoomScrollSensitivity"), config.zoomScrollSensitivity)
                    .setDefaultValue(0.2)
                    .setMin(0.01)
                    .setMax(2.0)
                    .setTooltip(Text.translatable("tooltip.trajectorys.zoomScrollSensitivity"))
                    .setSaveConsumer(newValue -> config.zoomScrollSensitivity = newValue)
                    .build());

            // --- Render ---
            render.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.trajectorys.renderLine"), config.renderLine)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> config.renderLine = newValue)
                    .build());

            render.addEntry(entryBuilder.startColorField(Text.translatable("option.trajectorys.lineColor"), config.lineColor)
                    .setDefaultValue(0xFF9600)
                    .setSaveConsumer(newValue -> config.lineColor = newValue)
                    .build());

            render.addEntry(entryBuilder.startBooleanToggle(Text.translatable("option.trajectorys.renderHitBox"), config.renderHitBox)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> config.renderHitBox = newValue)
                    .build());

            render.addEntry(entryBuilder.startDoubleField(Text.translatable("option.trajectorys.boxSize"), config.boxSize)
                    .setDefaultValue(0.1)
                    .setSaveConsumer(newValue -> config.boxSize = newValue)
                    .build());

            render.addEntry(entryBuilder.startColorField(Text.translatable("option.trajectorys.entityHitColor"), config.entityHitColor)
                    .setDefaultValue(0xFF0000)
                    .setSaveConsumer(newValue -> config.entityHitColor = newValue)
                    .build());

            render.addEntry(entryBuilder.startColorField(Text.translatable("option.trajectorys.blockHitColor"), config.blockHitColor)
                    .setDefaultValue(0x00FF00)
                    .setSaveConsumer(newValue -> config.blockHitColor = newValue)
                    .build());

            builder.setSavingRunnable(TrajectorysConfig::save);
            return builder.build();
        };
    }
}