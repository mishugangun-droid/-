package com.example.cheat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class CheatMod implements ClientModInitializer {

    static boolean killAura, autoTotem, fly, sprint, fullbright, noFall;

    static final String CAT = "category.cheatmod";
    static KeyBinding kKill, kTotem, kFly, kSprint, kBright, kNoFall;

    static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.cheatmod." + name, InputUtil.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        kKill   = key("killaura", GLFW.GLFW_KEY_R);
        kTotem  = key("autototem", GLFW.GLFW_KEY_G);
        kFly    = key("fly", GLFW.GLFW_KEY_F);
        kSprint = key("sprint", GLFW.GLFW_KEY_V);
        kBright = key("fullbright", GLFW.GLFW_KEY_B);
        kNoFall = key("nofall", GLFW.GLFW_KEY_N);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientPlayerEntity p = mc.player;
            if (p == null || mc.world == null) return;

            while (kKill.wasPressed())   killAura  = toggle(p, "KillAura", killAura);
            while (kTotem.wasPressed())  autoTotem = toggle(p, "AutoTotem", autoTotem);
            while (kSprint.wasPressed()) sprint    = toggle(p, "Sprint", sprint);
            while (kNoFall.wasPressed()) noFall    = toggle(p, "NoFall", noFall);
            while (kFly.wasPressed()) {
                fly = toggle(p, "Fly", fly);
                if (!fly && !p.isCreative() && !p.isSpectator()) {
                    p.getAbilities().flying = false;
                    p.getAbilities().allowFlying = false;
                }
            }
            while (kBright.wasPressed()) {
                fullbright = toggle(p, "Fullbright", fullbright);
                if (!fullbright) p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }

            if (killAura)   killAura(mc, p);
            if (autoTotem)  autoTotem(mc, p);
            if (fly)        { p.getAbilities().allowFlying = true; p.getAbilities().flying = true; }
            if (sprint && p.forwardSpeed > 0 && !p.isSneaking()) p.setSprinting(true);
            if (fullbright) p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false));
            if (noFall && p.fallDistance > 2f)
                p.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
        });
    }

    static boolean toggle(ClientPlayerEntity p, String name, boolean cur) {
        boolean v = !cur;
        p.sendMessage(Text.literal(name + ": " + (v ? "ON" : "OFF")), true);
        return v;
    }

    static void killAura(MinecraftClient mc, ClientPlayerEntity p) {
        if (p.getAttackCooldownProgress(0.5f) < 1f) return;
        Entity best = null;
        double bestDist = 4.2 * 4.2;
        for (Entity e : mc.world.getEntities()) {
            if (e == p || !(e instanceof LivingEntity le) || e instanceof ArmorStandEntity) continue;
            if (!le.isAlive()) continue;
            double d = p.squaredDistanceTo(e);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best != null) {
            mc.interactionManager.attackEntity(p, best);
            p.swingHand(Hand.MAIN_HAND);
        }
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
