package sharkus.extras;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class Hud {
    private static final ArrayDeque<Long> clicks = new ArrayDeque<>();
    private static boolean wasDown;

    // Schild-Cooldown-Tracking (Achsen sperren Schilde 5 Sekunden)
    private static boolean shieldCdPrev;
    private static long shieldCdStart;
    private static final long SHIELD_CD_MS = 5000;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static void tick(Minecraft mc) {
        if (mc.getWindow() == null) return;
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !wasDown) clicks.add(System.currentTimeMillis());
        wasDown = down;
        long now = System.currentTimeMillis();
        while (!clicks.isEmpty() && now - clicks.peek() > 1000) clicks.poll();

        if (mc.player != null) {
            boolean cd = mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD));
            if (cd && !shieldCdPrev) shieldCdStart = now;
            shieldCdPrev = cd;
        }
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("sharkus-extras", "hud"), Hud::render);
    }

    public static boolean isTeammate(Minecraft mc, Player p, Config c) {
        if (p.getTeam() != null && mc.player.getTeam() != null && p.isAlliedTo(mc.player)) return true;
        String n = p.getName().getString();
        for (String s : c.teamNames.split(",")) {
            if (!s.isBlank() && s.trim().equalsIgnoreCase(n)) return true;
        }
        return false;
    }

    // Standard-Position des Schild-Symbols (wird auch vom Editor benutzt)
    public static int shieldX(Config c, int sw) {
        return c.shieldX >= 0 ? c.shieldX : sw / 2 - 20;
    }

    public static int shieldY(Config c, int sh) {
        return c.shieldY >= 0 ? c.shieldY : sh - 90;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Config c = Config.I;
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float pt = delta.getGameTimeDeltaPartialTick(false);

        LivingEntity target = null;
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            target = le;
        }

        // ---------- FPS / CPS / Ping (einzeln verschiebbar) ----------
        if (c.fps) g.text(font, "FPS: " + mc.getFps(), c.fpsX, c.fpsY, 0xFFFFFFFF);
        if (c.cps) g.text(font, "CPS: " + clicks.size(), c.cpsX, c.cpsY, 0xFFFFFFFF);
        if (c.ping && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            g.text(font, "Ping: " + (info == null ? "?" : info.getLatency()) + " ms", c.pingX, c.pingY, 0xFFFFFFFF);
        }

        // ---------- Armor Display ----------
        if (c.armorDisplay) {
            int y = c.armorY;
            for (EquipmentSlot slot : ARMOR) {
                ItemStack stack = mc.player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                g.item(stack, c.armorX, y);
                if (stack.isDamageableItem()) {
                    int left = stack.getMaxDamage() - stack.getDamageValue();
                    g.text(font, String.valueOf(left), c.armorX + 20, y + 4, 0xFFFFFFFF);
                }
                y += 18;
            }
        }

        // ---------- Shield Status: Schild färbt sich von rot nach grün ----------
        if (c.shieldStatus) {
            ItemStack main = mc.player.getMainHandItem();
            ItemStack off = mc.player.getOffhandItem();
            ItemStack held = main.is(Items.SHIELD) ? main : (off.is(Items.SHIELD) ? off : ItemStack.EMPTY);
            boolean cd = mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD));
            if (!held.isEmpty() || cd) {
                ItemStack icon = held.isEmpty() ? new ItemStack(Items.SHIELD) : held;
                int bx = shieldX(c, sw), by = shieldY(c, sh);
                long rem = cd ? Math.max(0, SHIELD_CD_MS - (System.currentTimeMillis() - shieldCdStart)) : 0;
                double ratio = cd ? 1.0 - rem / (double) SHIELD_CD_MS : 1.0;
                int rgb = java.awt.Color.HSBtoRGB((float) (ratio * 0.33), 1f, 1f) & 0xFFFFFF;
                g.item(icon, bx + 12, by);
                g.fill(bx + 12, by, bx + 28, by + 16, (0x70 << 24) | rgb);
                String txt = cd ? String.format("%.1fs", rem / 1000.0) : (mc.player.isBlocking() ? "BLOCKT" : "BEREIT");
                g.text(font, txt, bx + 20 - font.width(txt) / 2, by + 19, 0xFF000000 | rgb);
            }
        }

        // ---------- Crosshair Indicator ----------
        if (c.crosshairIndicator) {
            float strength = mc.player.getAttackStrengthScale(0f);
            // Mitte des Vanilla-Fadenkreuzes (15x15 Bild, Mitte = Pixel 7)
            int cx = (sw - 15) / 2 + 7, cy = (sh - 15) / 2 + 7;
            if (target != null) {
                int color = strength >= 1.0f ? 0xFF30FF30 : 0xFFFF3030;
                drawCrosshair(g, c.crosshairStyle, cx, cy, color);
            }
            if (c.crosshairBar && strength < 1.0f) {
                g.fill(cx - 8, cy + 10, cx + 9, cy + 12, 0x80000000);
                g.fill(cx - 7, cy + 10, cx - 7 + (int) (15 * strength), cy + 12, 0xFFFFFFFF);
            }
        }

        // ---------- Team Tracker: Namen am Bildschirm + Liste ----------
        if (c.teamTracker) {
            Cam cam = new Cam(mc, pt, sw, sh);
            int ty = 4;
            for (Player p : mc.level.players()) {
                if (p == mc.player || !isTeammate(mc, p, c)) continue;
                double dist = mc.player.distanceTo(p);
                String name = Names.apply(p.getName().getString());

                String s = name + "  " + (int) dist + "m  " + (int) p.getHealth() + " HP";
                g.text(font, s, sw - font.width(s) - 4, ty, 0xFF55FF55);
                ty += 10;

                double ex = Cam.lerp(p.xo, p.getX(), pt), ey = Cam.lerp(p.yo, p.getY(), pt), ez = Cam.lerp(p.zo, p.getZ(), pt);
                String label = name + " " + (int) dist + "m";
                double[] pr = cam.proj(ex, ey + p.getBbHeight() + 1.9, ez);
                int half = font.width(label) / 2;
                int sx, sy;
                String shown = label;
                if (pr == null) {
                    boolean right = cam.side(ex, ez) >= 0;
                    sx = right ? sw - 14 - half : 14 + half;
                    sy = sh / 2;
                    shown = right ? label + " >" : "< " + label;
                } else {
                    sx = (int) Math.max(14 + half, Math.min(sw - 14 - half, pr[0]));
                    sy = (int) Math.max(14, Math.min(sh - 14, pr[1]));
                }
                g.text(font, shown, sx - font.width(shown) / 2, sy, 0xFF55FF55);
            }
        }
    }

    private static void drawCrosshair(GuiGraphicsExtractor g, int style, int cx, int cy, int color) {
        switch (style) {
            case 1 -> g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);                   // Punkt
            case 2 -> {                                                                  // Ring
                for (int a = 0; a < 360; a += 5) {
                    int x = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * 7);
                    int y = cy + (int) Math.round(Math.sin(Math.toRadians(a)) * 7);
                    g.fill(x, y, x + 1, y + 1, color);
                }
            }
            case 3 -> {                                                                  // Ecken
                int d = 7, l = 4;
                g.fill(cx - d, cy - d, cx - d + l, cy - d + 1, color);
                g.fill(cx - d, cy - d, cx - d + 1, cy - d + l, color);
                g.fill(cx + d - l + 1, cy - d, cx + d + 1, cy - d + 1, color);
                g.fill(cx + d, cy - d, cx + d + 1, cy - d + l, color);
                g.fill(cx - d, cy + d, cx - d + l, cy + d + 1, color);
                g.fill(cx - d, cy + d - l + 1, cx - d + 1, cy + d + 1, color);
                g.fill(cx + d - l + 1, cy + d, cx + d + 1, cy + d + 1, color);
                g.fill(cx + d, cy + d - l + 1, cx + d + 1, cy + d + 1, color);
            }
            case 4 -> {                                                                  // X (Hitmarker)
                for (int i = 3; i <= 7; i++) {
                    g.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, color);
                    g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
                    g.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
                    g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
                }
            }
            default -> {                                                                 // Kreuz
                g.fill(cx - 6, cy, cx + 7, cy + 1, color);
                g.fill(cx, cy - 6, cx + 1, cy + 7, color);
            }
        }
    }
}
