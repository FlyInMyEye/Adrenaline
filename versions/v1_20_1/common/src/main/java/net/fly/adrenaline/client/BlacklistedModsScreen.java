package net.fly.adrenaline.client;

import java.util.List;
import net.fly.adrenaline.AdrenalinePlatform.ModInfo;
import net.fly.adrenaline.compat.ModBlacklist;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class BlacklistedModsScreen extends Screen {
    private final Screen parent;
    private final List<ModInfo> mods = ModBlacklist.detected();
    private MultiLineLabel explanation = MultiLineLabel.EMPTY;

    public BlacklistedModsScreen(Screen parent) {
        super(Component.translatable("gui.adrenaline.blacklist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(560, this.width - 32);
        this.explanation = MultiLineLabel.create(this.font, Component.translatable("gui.adrenaline.blacklist.explanation"), contentWidth);
        List<ModEntry> entries = this.mods.stream().map(mod -> new ModEntry(mod, contentWidth - 22)).toList();
        int rowHeight = entries.stream().mapToInt(entry -> entry.lines.size() * this.font.lineHeight + 10).max().orElse(24);
        int top = 44 + this.explanation.getLineCount() * 12;
        ModList list = this.addRenderableWidget(new ModList(contentWidth, top, rowHeight));
        list.setLeftPos((this.width - contentWidth) / 2);
        entries.forEach(list::add);
        int buttonWidth = Math.min(180, (contentWidth - 8) / 2);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.blacklist.dismiss"), button -> {
            ModBlacklist.dismiss();
            this.onClose();
        }).bounds(this.width / 2 - buttonWidth - 4, this.height - 28, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.blacklist.close"), button -> this.onClose())
            .bounds(this.width / 2 + 4, this.height - 28, buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFF5555);
        this.explanation.renderCentered(graphics, this.width / 2, 36, 12, 0xFFE0E0E0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(new AdrenalineConfigScreen(this.parent));
    }

    private final class ModList extends ObjectSelectionList<ModEntry> {
        private ModList(int width, int top, int rowHeight) {
            super(BlacklistedModsScreen.this.minecraft, width, BlacklistedModsScreen.this.height, top,
                BlacklistedModsScreen.this.height - 40, rowHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
            this.setRenderSelection(false);
        }

        private void add(ModEntry entry) {
            this.addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return this.width - 18;
        }

        @Override
        public int getRowLeft() {
            return this.x0 + 2;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.x1 - 8;
        }
    }

    private final class ModEntry extends ObjectSelectionList.Entry<ModEntry> {
        private final Component text;
        private final List<FormattedCharSequence> lines;

        private ModEntry(ModInfo mod, int width) {
            this.text = Component.literal(mod.name() + " - ").append(mod.fileName().isEmpty()
                ? Component.translatable("gui.adrenaline.blacklist.unknown_file") : Component.literal(mod.fileName()));
            this.lines = BlacklistedModsScreen.this.font.split(this.text, width);
        }

        @Override
        public Component getNarration() {
            return this.text;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height,
            int mouseX, int mouseY, boolean hovered, float partialTick) {
            for (FormattedCharSequence line : this.lines) {
                graphics.drawString(BlacklistedModsScreen.this.font, line, x + 2, y + 4, 0xFFFFFFFF);
                y += BlacklistedModsScreen.this.font.lineHeight;
            }
        }
    }
}
