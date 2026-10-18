package com.example.cheat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class CheatGui extends Screen {

    public CheatGui() {
        super(Text.literal("CheatMod"));
    }

    static Text label(CheatMod.Mod m) {
        return Text.literal(m.name + (m.on ? " \u00a7aON" : " \u00a7cOFF"));
    }

    private void add(CheatMod.Mod m, int x, int y, int w, int h) {
        addDrawableChild(ButtonWidget.builder(label(m), b -> {
            m.on = !m.on;
            b.setMessage(label(m));
        }).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        int w = 105, h = 20, step = 22;
        int lx = width / 2 - w - 5, rx = width / 2 + 5, y0 = 28;

        List<CheatMod.Mod> left = List.of(CheatMod.KILL, CheatMod.TOTEM, CheatMod.FLY,
                CheatMod.SPRINT, CheatMod.NOFALL);
        List<CheatMod.Mod> right = List.of(CheatMod.BRIGHT, CheatMod.ESP, CheatMod.ITEMESP,
                CheatMod.TARGET, CheatMod.AURA, CheatMod.TRAIL, CheatMod.HITFX, CheatMod.HUD);

        int y = y0;
        for (CheatMod.Mod m : left) { add(m, lx, y, w, h); y += step; }
        addDrawableChild(ButtonWidget.builder(Text.literal("Range: " + CheatMod.range), b -> {
            CheatMod.cycleRange();
            b.setMessage(Text.literal("Range: " + CheatMod.range));
        }).dimensions(lx, y, w, h).build());

        y = y0;
        for (CheatMod.Mod m : right) { add(m, rx, y, w, h); y += step; }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "CheatMod", width / 2, 10, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_K) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
