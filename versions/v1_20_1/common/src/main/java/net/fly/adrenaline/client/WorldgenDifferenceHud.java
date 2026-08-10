package net.fly.adrenaline.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fly.adrenaline.util.WorldgenDifferenceState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class WorldgenDifferenceHud {

    private WorldgenDifferenceHud() {
    }

    public static void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        BlockPos pos = targetedDifference(minecraft);
        if (pos == null) {
            return;
        }

        WorldgenDifferenceState.Difference difference = WorldgenDifferenceState.difference(server, pos);
        if (difference == null) {
            return;
        }

        drawPanel(guiGraphics, minecraft.font, stateLines("VANILLA", difference.vanillaStateId(), 0x55FF55), false);
        drawPanel(guiGraphics, minecraft.font, stateLines("ADRENALINE", difference.adrenalineStateId(), 0x55FFFF), true);
    }

    public static BlockPos targetedDifference(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (!WorldgenDifferenceState.isActive(server) || minecraft.player == null) {
            return null;
        }
        if (minecraft.hitResult instanceof BlockHitResult hitResult
            && WorldgenDifferenceState.difference(server, hitResult.getBlockPos()) != null) {
            return hitResult.getBlockPos().immutable();
        }

        Vec3 eye = minecraft.player.getEyePosition(minecraft.getFrameTime());
        Vec3 direction = minecraft.player.getViewVector(minecraft.getFrameTime());
        double maxDistance = 6.0D;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            maxDistance = Math.min(maxDistance, eye.distanceTo(minecraft.hitResult.getLocation()) + 0.1D);
        }
        BlockPos previous = null;
        for (double distance = 0.25D; distance <= maxDistance; distance += 0.05D) {
            Vec3 point = eye.add(direction.scale(distance));
            BlockPos candidate = BlockPos.containing(point);
            if (!candidate.equals(previous) && WorldgenDifferenceState.difference(server, candidate) != null) {
                return candidate.immutable();
            }
            previous = candidate;
        }
        return null;
    }

    private static List<Line> stateLines(String title, int stateId, int titleColor) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(title, titleColor));
        BlockState state = Block.BLOCK_STATE_REGISTRY.byId(stateId);
        if (state == null) {
            lines.add(new Line(Integer.toString(stateId), 0xFFFFFF));
            return lines;
        }

        lines.add(new Line(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 0xFFFFFF));
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            lines.add(new Line(entry.getKey().getName() + ": " + entry.getValue(), 0xB0B0B0));
        }
        return lines;
    }

    private static void drawPanel(GuiGraphics guiGraphics, Font font, List<Line> lines, boolean right) {
        int y = 5;
        for (Line line : lines) {
            int width = font.width(line.text());
            int x = right ? guiGraphics.guiWidth() - width - 5 : 5;
            guiGraphics.fill(x - 2, y - 2, x + width + 2, y + font.lineHeight + 1, 0x90000000);
            guiGraphics.drawString(font, line.text(), x, y, line.color(), false);
            y += font.lineHeight + 3;
        }
    }

    private record Line(String text, int color) {
    }
}
