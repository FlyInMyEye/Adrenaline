package net.fly.adrenaline.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.client.AdrenalineSettings.Group;
import net.fly.adrenaline.client.AdrenalineSettings.Setting;
import net.fly.adrenaline.compat.ModBlacklist;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.config.AdrenalineConfig.Data;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

public class AdrenalineConfigScreen extends Screen {
    private final Screen parent;
    private final Session session;
    private final boolean advanced;
    private SettingsList options;
    private SettingsList categories;
    private EditBox search;
    private Button apply;
    private Button categoryButton;
    private boolean sidebar;
    private int contentLeft;
    private int contentWidth;
    private int listTop;
    private int listBottom;
    private Group group = Group.ALL;
    private String query = "";
    private final List<Setting> visibleSettings = new ArrayList<>();
    private double lastScroll;

    public AdrenalineConfigScreen(Screen parent) {
        this(parent, new Session(), false);
    }

    public static Screen create(Screen parent) {
        return ModBlacklist.shouldWarn() ? new BlacklistedModsScreen(parent) : new AdrenalineConfigScreen(parent);
    }

    private AdrenalineConfigScreen(Screen parent, Session session, boolean advanced) {
        super(Component.translatable(advanced ? "gui.adrenaline.config.advanced_title" : "gui.adrenaline.config.title"));
        this.parent = parent;
        this.session = session;
        this.advanced = advanced;
    }

    @Override
    protected void init() {
        this.contentWidth = Math.min(this.width - 32, this.advanced ? 760 : 560);
        this.contentLeft = (this.width - this.contentWidth) / 2;
        this.sidebar = this.advanced && this.contentWidth >= 680;
        int sidebarWidth = this.sidebar ? 120 : 0;
        this.listTop = this.advanced ? 64 : 36;
        boolean twoFooterRows = this.contentWidth < 400;
        this.listBottom = this.height - (twoFooterRows ? 62 : 36);
        int left = this.contentLeft;
        int contentWidth = this.contentWidth;
        this.categories = null;
        this.categoryButton = null;
        if (this.advanced) {
            int searchLeft = left + (this.sidebar ? sidebarWidth : 128);
            this.search = new EditBox(this.font, searchLeft, 36, contentWidth - (searchLeft - left), 20,
                Component.translatable("gui.adrenaline.config.search"));
            this.search.setHint(Component.translatable("gui.adrenaline.config.search"));
            this.search.setMaxLength(128);
            this.search.setValue(this.query);
            this.search.setResponder(value -> {
                this.query = value;
                this.populateOptions(false);
            });
            this.addRenderableWidget(this.search);
            if (this.sidebar) {
                this.categories = this.addRenderableWidget(this.createCategories(sidebarWidth - 8, this.listTop));
                this.categories.setLeftPos(left);
            } else {
                this.categoryButton = this.addRenderableWidget(Button.builder(this.group.title(), button -> this.toggleCategories())
                    .bounds(left, 36, 120, 20).build());
            }
        }
        this.options = this.addRenderableWidget(new SettingsList(contentWidth - sidebarWidth, this.listTop, 24));
        this.options.setLeftPos(left + sidebarWidth);
        this.populateOptions(true);
        this.options.setScrollAmount(this.lastScroll);

        int gap = 4;
        int buttonWidth = twoFooterRows ? (contentWidth - gap * 2) / 3 : (contentWidth - gap * 4) / 5;
        int bottom = this.height - 28;
        int navigationY = twoFooterRows ? bottom - 24 : bottom;
        int actionLeft = twoFooterRows ? left : left + (buttonWidth + gap) * 2;
        this.addRenderableWidget(Button.builder(Component.translatable(this.advanced ? "gui.back" : "gui.adrenaline.config.advanced"), button -> {
            this.lastScroll = this.options.getScrollAmount();
            if (this.advanced) {
                this.minecraft.setScreen(this.parent);
            } else {
                this.minecraft.setScreen(new AdrenalineConfigScreen(this, this.session, true));
            }
        }).bounds(left, navigationY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.config.reset_page"), button -> {
            for (Setting setting : this.visibleSettings) {
                setting.copy.accept(this.session.draft, AdrenalineSettings.DEFAULTS);
            }
            this.populateOptions(true);
        }).bounds(left + (buttonWidth + gap), navigationY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.closeToParent())
            .bounds(actionLeft, bottom, buttonWidth, 20).build());
        this.apply = this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.config.apply"), button -> this.save())
            .bounds(actionLeft + buttonWidth + gap, bottom, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            this.save();
            this.closeToParent();
        }).bounds(actionLeft + (buttonWidth + gap) * 2, bottom, buttonWidth, 20).build());
        this.updateControls();
    }

    private void populateOptions(boolean preserveScroll) {
        if (this.options == null) {
            return;
        }
        double scroll = preserveScroll ? this.options.getScrollAmount() : 0;
        this.options.clear();
        this.visibleSettings.clear();
        Group previousGroup = null;
        String filter = this.query.strip().toLowerCase(Locale.ROOT);
        List<Setting> settings = this.advanced ? AdrenalineSettings.ALL : AdrenalineSettings.ALL.stream()
            .filter(setting -> setting.main)
            .sorted(java.util.Comparator.comparingInt(setting -> switch (setting.group) {
                case SCHEDULING -> 0;
                case STARTUP, WARMUP -> 1;
                default -> 2;
            })).toList();
        for (Setting setting : settings) {
            if ((!this.advanced && !setting.main) || (this.advanced && this.group != Group.ALL && this.group != setting.group)) {
                continue;
            }
            String text = setting.title().getString() + " " + setting.description().getString() + " " + setting.id + " " + setting.group.title().getString();
            if (this.advanced && !text.toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            if (previousGroup != setting.group) {
                if (!this.advanced || this.group == Group.ALL) {
                    if (this.advanced || previousGroup == null || !this.mainGroupTitle(previousGroup).equals(this.mainGroupTitle(setting.group))) {
                        this.options.add(new HeaderRow(this.advanced ? setting.group.title() : this.mainGroupTitle(setting.group)));
                    }
                }
                previousGroup = setting.group;
            }
            this.visibleSettings.add(setting);
            this.options.add(new SettingRow(setting));
        }
        if (this.advanced && (this.group == Group.ALL || this.group == Group.DIAGNOSTICS) && BuildConfig.DEBUG
            && (filter.isEmpty() || ("benchmark worldgen debug " + Component.translatable("gui.adrenaline.config.worldgen_benchmark").getString()).toLowerCase(Locale.ROOT).contains(filter))) {
            if (previousGroup != Group.DIAGNOSTICS) {
                this.options.add(new HeaderRow(Group.DIAGNOSTICS.title()));
            }
            this.options.add(new BenchmarkRow());
        }
        this.options.setScrollAmount(scroll);
        this.updateControls();
    }

    private Component mainGroupTitle(Group category) {
        return Component.translatable("gui.adrenaline.config.main_group." + switch (category) {
            case SCHEDULING -> "performance";
            case SAVING -> "saving";
            default -> "loading";
        });
    }

    private void save() {
        AdrenalineConfig.save(new Data(this.session.draft));
        this.session.saved = new Data(this.session.draft);
        this.updateControls();
    }

    private void updateControls() {
        if (this.apply != null) {
            this.apply.active = this.session.dirty();
        }
        if (this.options != null) {
            for (Row row : this.options.children()) {
                row.update();
            }
        }
    }

    private void closeToParent() {
        Screen target = this.parent;
        while (target instanceof AdrenalineConfigScreen screen) {
            target = screen.parent;
        }
        this.minecraft.setScreen(target);
    }

    @Override
    public void onClose() {
        if (this.advanced) {
            this.minecraft.setScreen(this.parent);
        } else {
            this.closeToParent();
        }
    }

    @Override
    public void tick() {
        if (this.search != null) {
            this.search.tick();
        }
        this.updateControls();
    }

    @Override
    public void removed() {
        if (this.options != null) {
            this.lastScroll = this.options.getScrollAmount();
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!this.sidebar && this.categories != null && key == GLFW.GLFW_KEY_ESCAPE) {
            this.closeCategories();
            return true;
        }
        if (this.search != null && key == GLFW.GLFW_KEY_F && hasControlDown()) {
            this.closeCategories();
            this.setFocused(this.search);
            this.search.setFocused(true);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, delta);
        if (this.options.children().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.adrenaline.config.no_results"), this.options.getRowLeft() + this.options.getRowWidth() / 2, this.listTop + 16, 0xA0A0A0);
        }
    }

    private SettingsList createCategories(int width, int top) {
        SettingsList list = new SettingsList(width, top, 24);
        for (Group category : Group.values()) {
            if (category != Group.DIAGNOSTICS || BuildConfig.DEBUG) {
                list.add(new CategoryRow(category));
            }
        }
        return list;
    }

    private void toggleCategories() {
        if (this.categories != null) {
            this.closeCategories();
            return;
        }
        this.categories = this.createCategories(160, this.listTop);
        this.categories.setLeftPos(this.contentLeft);
        this.addRenderableWidget(this.categories);
        this.setFocused(this.categories);
    }

    private void closeCategories() {
        if (!this.sidebar && this.categories != null) {
            this.removeWidget(this.categories);
            this.categories = null;
            this.setFocused(this.categoryButton);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.sidebar && this.categories != null) {
            this.categories.mouseScrolled(mouseX, mouseY, delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.sidebar && this.categories != null) {
            if (this.categories.isMouseOver(mouseX, mouseY)) {
                SettingsList popup = this.categories;
                boolean handled = popup.mouseClicked(mouseX, mouseY, button);
                if (handled && this.categories == popup) {
                    this.setFocused(popup);
                    this.setDragging(button == 0);
                }
                return handled;
            }
            this.closeCategories();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private final class SettingsList extends ContainerObjectSelectionList<Row> {
        private SettingsList(int width, int top, int rowHeight) {
            super(AdrenalineConfigScreen.this.minecraft, width, AdrenalineConfigScreen.this.height, top, AdrenalineConfigScreen.this.listBottom, rowHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
            this.setRenderSelection(false);
        }

        private void add(Row row) {
            this.addEntry(row);
        }

        private void clear() {
            this.setFocused(null);
            this.clearEntries();
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

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            boolean popup = this == AdrenalineConfigScreen.this.categories && !AdrenalineConfigScreen.this.sidebar;
            if (popup) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.fill(this.x0, this.y0 - 2, this.x1, this.y1 + 2, 0xFF202020);
            }
            boolean covered = this == AdrenalineConfigScreen.this.options && !AdrenalineConfigScreen.this.sidebar && AdrenalineConfigScreen.this.categories != null;
            super.render(graphics, covered ? -1 : mouseX, covered ? -1 : mouseY, delta);
            if (popup) {
                graphics.pose().popPose();
            }
        }
    }

    private abstract class Row extends ContainerObjectSelectionList.Entry<Row> {
        final List<AbstractWidget> widgets = new ArrayList<>();

        void update() {
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.widgets;
        }
    }

    private final class HeaderRow extends Row {
        private final Component name;

        private HeaderRow(Component name) {
            this.name = name;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            graphics.drawString(AdrenalineConfigScreen.this.font, this.name.copy().withStyle(net.minecraft.ChatFormatting.BOLD), x + 2, y + 8, 0xFF59BD);
        }
    }

    private final class CategoryRow extends Row {
        private final Group category;
        private final Button button;

        private CategoryRow(Group category) {
            this.category = category;
            this.button = Button.builder(category.title(), pressed -> {
                AdrenalineConfigScreen.this.group = category;
                if (AdrenalineConfigScreen.this.categoryButton != null) {
                    AdrenalineConfigScreen.this.categoryButton.setMessage(category.title());
                }
                AdrenalineConfigScreen.this.closeCategories();
                AdrenalineConfigScreen.this.populateOptions(false);
            }).bounds(0, 0, 100, 20).build();
            this.widgets.add(this.button);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            this.button.setPosition(x, y);
            this.button.setWidth(width);
            this.button.active = AdrenalineConfigScreen.this.group != this.category;
            this.button.render(graphics, mouseX, mouseY, delta);
        }
    }

    private final class SettingRow extends Row {
        private final Setting setting;
        private final AbstractWidget control;
        private final Button reset;

        private SettingRow(Setting setting) {
            this.setting = setting;
            this.control = setting.integer != null ? new IntegerSlider(setting)
                : Button.builder(setting.label.apply(AdrenalineConfigScreen.this.session.draft), pressed -> {
                    setting.cycle.accept(AdrenalineConfigScreen.this.session.draft);
                    AdrenalineConfigScreen.this.updateControls();
                }).bounds(0, 0, 88, 20).build();
            this.reset = Button.builder(Component.translatable("gui.adrenaline.config.reset_option_button"), pressed -> {
                setting.copy.accept(AdrenalineConfigScreen.this.session.draft, AdrenalineSettings.DEFAULTS);
                if (this.control instanceof IntegerSlider slider) {
                    slider.sync();
                }
                AdrenalineConfigScreen.this.updateControls();
            }).bounds(0, 0, 44, 20).build();
            this.widgets.add(this.control);
            this.widgets.add(this.reset);
            this.update();
        }

        @Override
        void update() {
            Component label = this.setting.label.apply(AdrenalineConfigScreen.this.session.draft);
            this.reset.active = !this.setting.equal.test(AdrenalineConfigScreen.this.session.draft, AdrenalineSettings.DEFAULTS);
            MutableComponent tooltip = this.setting.title().copy().append("\n").append(this.setting.description());
            Component reason = this.setting.inactiveReason(AdrenalineConfigScreen.this.session.draft);
            this.control.active = reason == null;
            this.control.setMessage(reason == null ? label : label.copy().withStyle(style -> style.withColor(0xA0A0A0)));
            if (reason != null) {
                tooltip.append("\n\n").append(reason.copy().withStyle(style -> style.withColor(0xFFCC66)));
            }
            tooltip.append("\n\n").append(Component.translatable("tooltip.adrenaline.performance_impact", this.setting.impact.label()));
            tooltip.append("\n").append(Component.translatable("gui.adrenaline.config.default_value", this.setting.label.apply(AdrenalineSettings.DEFAULTS)));
            if (AdrenalineConfigScreen.this.advanced) {
                tooltip.append("\n").append(Component.literal("adrenaline.json: " + this.setting.id).withStyle(style -> style.withColor(0x929DAF)));
            }
            this.control.setTooltip(Tooltip.create(tooltip));
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            Data draft = AdrenalineConfigScreen.this.session.draft;
            if (hovered) {
                graphics.fill(x, y, x + width, y + 20, 0x20FFFFFF);
            }
            int resetWidth = Math.max(44, AdrenalineConfigScreen.this.font.width(this.reset.getMessage()) + 12);
            int controlWidth = Math.min(120, Math.max(88, width / 3));
            int controlX = x + width - resetWidth - 4 - controlWidth;
            int textWidth = controlX - x - 8;
            Component name = this.setting.title();
            if (!this.setting.equal.test(draft, AdrenalineConfigScreen.this.session.saved)) {
                name = name.copy().append(" *");
            }
            List<net.minecraft.util.FormattedCharSequence> lines = AdrenalineConfigScreen.this.font.split(name, textWidth);
            int count = Math.min(2, lines.size());
            int textY = y + (20 - count * AdrenalineConfigScreen.this.font.lineHeight) / 2 + 1;
            for (int line = 0; line < count; line++) {
                graphics.drawString(AdrenalineConfigScreen.this.font, lines.get(line), x + 2, textY + line * AdrenalineConfigScreen.this.font.lineHeight, this.control.active ? 0xFFFFFF : 0xA0A0A0);
            }
            this.control.setPosition(controlX, y);
            this.control.setWidth(controlWidth);
            this.reset.setPosition(x + width - resetWidth, y);
            this.reset.setWidth(resetWidth);
            this.control.render(graphics, mouseX, mouseY, delta);
            this.reset.render(graphics, mouseX, mouseY, delta);
            if (hovered && mouseX < this.control.getX() && mouseY >= y && mouseY < y + 20) {
                AdrenalineConfigScreen.this.setTooltipForNextRenderPass(this.control.getTooltip(), net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, true);
            }
        }
    }

    private final class IntegerSlider extends AbstractSliderButton {
        private final Setting setting;

        private IntegerSlider(Setting setting) {
            super(0, 0, 88, 20, Component.empty(), 0);
            this.setting = setting;
            this.sync();
        }

        private void sync() {
            this.value = Math.max(0, Math.min(1, (double) (this.setting.integer.applyAsInt(AdrenalineConfigScreen.this.session.draft) - this.setting.min) / (this.setting.max - this.setting.min)));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(this.setting.label.apply(AdrenalineConfigScreen.this.session.draft));
        }

        @Override
        protected void applyValue() {
            int value = this.setting.min + (int) Math.round(this.value * (this.setting.max - this.setting.min));
            this.setting.setInteger.accept(AdrenalineConfigScreen.this.session.draft, value);
            this.updateMessage();
            AdrenalineConfigScreen.this.updateControls();
        }
    }

    private final class BenchmarkRow extends Row {
        private final Button button;

        private BenchmarkRow() {
            this.button = Button.builder(Component.translatable("gui.adrenaline.config.worldgen_benchmark"), pressed -> {
                AdrenalineConfigScreen.this.save();
                if (WorldgenBenchmark.start(AdrenalineConfigScreen.this.minecraft, AdrenalineConfigScreen.this.session.draft)) {
                    AdrenalineConfigScreen.this.minecraft.setScreen(new WorldgenBenchmarkScreen(AdrenalineConfigScreen.this));
                }
            }).bounds(0, 0, 200, 20).build();
            this.widgets.add(this.button);
        }

        @Override
        void update() {
            this.button.active = AdrenalineConfigScreen.this.minecraft.level == null
                && AdrenalineConfigScreen.this.minecraft.getSingleplayerServer() == null
                && !WorldgenBenchmark.isRunning() && !BackgroundWorldgenWarmup.isRunning() && !BackgroundWorldSave.isRunning();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            this.button.setPosition(x, y);
            this.button.setWidth(width);
            this.button.render(graphics, mouseX, mouseY, delta);
        }
    }

    private static final class Session {
        private final Data draft = AdrenalineConfig.copy();
        private Data saved = new Data(this.draft);

        private boolean dirty() {
            return AdrenalineSettings.ALL.stream().anyMatch(setting -> !setting.equal.test(this.draft, this.saved));
        }
    }
}
