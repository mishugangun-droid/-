package com.example.cheat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CheatMod implements ClientModInitializer {

    public static class Mod {
        public final String name;
        public boolean on;
        Mod(String name) { this.name = name; }
    }

    public static final Mod KILL = new Mod("KillAura");
    public static final Mod TOTEM = new Mod("AutoTotem");
    public static final Mod FLY = new Mod("Fly");
    public static final Mod SPRINT = new Mod("Sprint");
    public static final Mod NOFALL = new Mod("NoFall");
    public static final Mod BRIGHT = new Mod("Fullbright");
    public static final Mod ESP = new Mod("ESP");
    public static final Mod ITEMESP = new Mod("ItemESP");
    public static final Mod TARGET = new Mod("TargetESP");
    public static final Mod AURA = new Mod("Particle Aura");
    public static final Mod TRAIL = new Mod("Trail");
    public static final Mod HITFX = new Mod("Hit Effects");
    public static final Mod HUD = new Mod("HUD");
    public static final List<Mod> ALL = List.of(KILL, TOTEM, FLY, SPRINT, NOFALL, BRIGHT,
            ESP, ITEMESP, TARGET, AURA, TRAIL, HITFX, HUD);

    public static double range = 4.2;
    static final double[] RANGES = {3.0, 4.2, 5.0, 6.0};

    public static void cycleRange() {
        int idx = 0;
        for (int i = 0; i < RANGES.length; i++) if (RANGES[i] == range) idx = i;
        range = RANGES[(idx + 1) % RANGES.length];
    }

    static KeyBinding kGui;
    static LivingEntity target;
    static boolean wFly, wBright, wEsp, wItem;

    static int rainbow(float offset) {
        float h = ((System.currentTimeMillis() % 4000) / 4000f + offset) % 1f;
        return 0xFF000000 | MathHelper.hsvToRgb(h, 0.7f, 1f);
    }

    @Override
    public void onInitializeClient() {
        kGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cheatmod.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.cheatmod"));

        // Искры при ударе
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (HITFX.on && world.isClient && entity instanceof LivingEntity) {
                for (int i = 0; i < 14; i++) {
                    double vx = (world.getRandom().nextDouble() - 0.5) * 0.8;
                    double vy = world.getRandom().nextDouble() * 0.5;
                    double vz = (world.getRandom().nextDouble() - 0.5) * 0.8;
                    world.addParticle(ParticleTypes.CRIT, entity.getX(), entity.getBodyY(0.5), entity.getZ(), vx, vy, vz);
                    if (i % 2 == 0)
                        world.addParticle(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getBodyY(0.5), entity.getZ(), vx, vy, vz);
                }
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null) return;

            while (kGui.wasPressed()) mc.setScreen(new CheatGui());
            long t = mc.world.getTime();

            // Цель
            LivingEntity tgt = null;
            if (KILL.on) tgt = closest(mc, p);
            else if (mc.targetedEntity instanceof LivingEntity le) tgt = le;
            target = tgt;

            if (KILL.on && tgt != null && p.getAttackCooldownProgress(0.5f) >= 1f) {
                mc.interactionManager.attackEntity(p, tgt);
                p.swingHand(Hand.MAIN_HAND);
            }
            if (TOTEM.on) autoTotem(mc, p);

            if (FLY.on) {
                p.getAbilities().allowFlying = true;
                p.getAbilities().flying = true;
            } else if (wFly && !p.isCreative() && !p.isSpectator()) {
                p.getAbilities().flying = false;
                p.getAbilities().allowFlying = false;
            }
            wFly = FLY.on;

            if (SPRINT.on && p.forwardSpeed > 0 && !p.isSneaking()) p.setSprinting(true);

            if (BRIGHT.on) p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false));
            else if (wBright) p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            wBright = BRIGHT.on;

            if (NOFALL.on && p.fallDistance > 2f)
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));

            // Подсветка сквозь стены
            if (ESP.on || wEsp || ITEMESP.on || wItem) {
                for (Entity e : mc.world.getEntities()) {
                    if (e instanceof ItemEntity) {
                        if (ITEMESP.on || wItem) e.setGlowing(ITEMESP.on);
                    } else if (e instanceof LivingEntity && e != p && !(e instanceof ArmorStandEntity)) {
                        if (ESP.on || wEsp) e.setGlowing(ESP.on);
                    }
                }
            }
            wEsp = ESP.on;
            wItem = ITEMESP.on;

            // Частицы
            if (AURA.on) {
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.2 + i * (Math.PI * 2 / 3);
                    double y = p.getY() + 1.0 + Math.sin(t * 0.1 + i) * 0.7;
                    mc.world.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME,
                            p.getX() + Math.cos(a) * 0.9, y, p.getZ() + Math.sin(a) * 0.9, 0, 0, 0);
                }
            }
            if (TRAIL.on && p.getVelocity().horizontalLengthSquared() > 0.003) {
                mc.world.addParticle(ParticleTypes.FLAME, p.getX(), p.getY() + 0.1, p.getZ(), 0, 0.02, 0);
            }
        });

        // TargetESP: рамка и бегающее кольцо
        WorldRenderEvents.LAST.register(ctx -> {
            if (!TARGET.on || target == null || !target.isAlive()) return;
            VertexConsumerProvider vcp = ctx.consumers();
            MatrixStack ms = ctx.matrixStack();
            if (vcp == null || ms == null) return;

            float td = ctx.tickCounter().getTickDelta(false);
            Vec3d cam = ctx.camera().getPos();
            Entity e = target;
            Vec3d pos = e.getLerpedPos(td);

            VertexConsumer buf = vcp.getBuffer(RenderLayer.getLines());
            ms.push();
            ms.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            MatrixStack.Entry en = ms.peek();

            float w = e.getWidth() / 2f, h = e.getHeight();
            box(buf, en, -w, 0, -w, w, h, w, rainbow(0f));

            double time = (System.currentTimeMillis() % 2000) / 2000.0;
            float ry = (float) ((Math.sin(time * Math.PI * 2) + 1) / 2 * h);
            float rad = w * 1.4f + 0.2f;
            int seg = 36;
            for (int i = 0; i < seg; i++) {
                double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                line(buf, en,
                        (float) Math.cos(a1) * rad, ry, (float) Math.sin(a1) * rad,
                        (float) Math.cos(a2) * rad, ry, (float) Math.sin(a2) * rad,
                        rainbow(i / (float) seg));
            }
            ms.pop();
            if (vcp instanceof VertexConsumerProvider.Immediate imm) imm.draw(RenderLayer.getLines());
        });

        // HUD: радужная надпись и список функций
        HudRenderCallback.EVENT.register((ctx, tc) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!HUD.on || mc.options.hudHidden) return;
            var tr = mc.textRenderer;

            String wm = "CheatMod";
            int x = 4;
            for (int i = 0; i < wm.length(); i++) {
                String ch = String.valueOf(wm.charAt(i));
                ctx.drawTextWithShadow(tr, ch, x, 4, rainbow(i * 0.08f));
                x += tr.getWidth(ch);
            }

            List<Mod> act = new ArrayList<>();
            for (Mod m : ALL) if (m.on && m != HUD) act.add(m);
            act.sort((a, b) -> tr.getWidth(b.name) - tr.getWidth(a.name));
            int y = 4, sw = ctx.getScaledWindowWidth(), idx = 0;
            for (Mod m : act) {
                ctx.drawTextWithShadow(tr, m.name, sw - tr.getWidth(m.name) - 4, y, rainbow(idx * 0.07f));
                y += 10;
                idx++;
            }
        });
    }

    static void line(VertexConsumer b, MatrixStack.Entry e, float x1, float y1, float z1,
                     float x2, float y2, float z2, int col) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        dx /= len; dy /= len; dz /= len;
        int r = (col >> 16) & 255, g = (col >> 8) & 255, bl = col & 255;
        b.vertex(e, x1, y1, z1).color(r, g, bl, 255).normal(e, dx, dy, dz);
        b.vertex(e, x2, y2, z2).color(r, g, bl, 255).normal(e, dx, dy, dz);
    }

    static void box(VertexConsumer b, MatrixStack.Entry e, float x1, float y1, float z1,
                    float x2, float y2, float z2, int c) {
        line(b, e, x1, y1, z1, x2, y1, z1, c); line(b, e, x2, y1, z1, x2, y1, z2, c);
        line(b, e, x2, y1, z2, x1, y1, z2, c); line(b, e, x1, y1, z2, x1, y1, z1, c);
        line(b, e, x1, y2, z1, x2, y2, z1, c); line(b, e, x2, y2, z1, x2, y2, z2, c);
        line(b, e, x2, y2, z2, x1, y2, z2, c); line(b, e, x1, y2, z2, x1, y2, z1, c);
        line(b, e, x1, y1, z1, x1, y2, z1, c); line(b, e, x2, y1, z1, x2, y2, z1, c);
        line(b, e, x2, y1, z2, x2, y2, z2, c); line(b, e, x1, y1, z2, x1, y2, z2, c);
    }

    static LivingEntity closest(MinecraftClient mc, ClientPlayerEntity p) {
        LivingEntity best = null;
        double bd = range * range;
        for (Entity e : mc.world.getEntities()) {
            if (e == p || !(e instanceof LivingEntity le) || e instanceof ArmorStandEntity || !le.isAlive()) continue;
            double d = p.squaredDistanceTo(e);
            if (d < bd) { bd = d; best = le; }
        }
        return best;
    }

    static void autoTotem(MinecraftClient mc, ClientPlayerEntity p) {
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (mc.currentScreen != null) return;
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                int slot = i < 9 ? 36 + i : i;
                mc.interactionManager.clickSlot(
                        p.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, p);
                return;
            }
        }
    }
                                  }
