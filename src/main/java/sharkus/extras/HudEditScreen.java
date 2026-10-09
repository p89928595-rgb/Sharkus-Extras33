package sharkus.extras;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Elemente mit gedrückter linker Maustaste verschieben. */
public class HudEditScreen extends Screen {
    private static final class El {
        final String name;
        final IntSupplier gx, gy;
        final IntConsumer sx, sy;
        final int w, h;

        El(String name, IntSupplier gx, IntSupplier gy, IntConsumer sx, IntConsumer sy, int w, int h) {
            this.name = name;
            this.gx = gx;
            this.gy = gy;
            this.sx = sx;
            this.sy = sy;
            this.w = w;
            this.h = h;
        }
    }

    private final Screen parent;
    private final List<El> els = new ArrayList<>();
    private El drag;
    private int offX, offY;
    private boolean wasDown;

    public HudEditScreen(Screen parent) {
        super(Component.literal("HUD verschieben"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config c = Config.I;
        els.clear();
        els.add(new El("FPS", () -> c.fpsX, () -> c.fpsY, v -> c.fpsX = v, v -> c.fpsY = v, 50, 10));
        els.add(new El("CPS", () -> c.cpsX, () -> c.cpsY, v -> c.cpsX = v, v -> c.cpsY = v, 50, 10));
        els.add(new El("Ping", () -> c.pingX, () -> c.pingY, v -> c.pingX = v, v -> c.pingY = v, 70, 10));
        els.add(new El("Armor", () -> c.armorX, () -> c.armorY, v -> c.armorX = v, v -> c.armorY = v, 50, 72));
        els.add(new El("Schild", () -> Hud.shieldX(c, width), () -> Hud.shieldY(c, height),
                v -> c.shieldX = v, v -> c.shieldY = v, 40, 30));

        addRenderableWidget(Button.builder(Component.literal("Zurücksetzen"), b -> {
            c.resetPositions();
            Config.save();
        }).bounds(width / 2 - 104, height - 28, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
                .bounds(width / 2 + 4, height - 28, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("Elemente mit gedrückter linker Maustaste ziehen"), width / 2, 20, 0xFFAAAAAA);

        boolean down = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down) {
            if (drag == null && !wasDown && mouseY < height - 34) {
                for (El e : els) {
                    int x = e.gx.getAsInt(), y = e.gy.getAsInt();
                    if (mouseX >= x && mouseX <= x + e.w && mouseY >= y && mouseY <= y + e.h) {
                        drag = e;
                        offX = mouseX - x;
                        offY = mouseY - y;
                        break;
                    }
                }
            }
            if (drag != null) {
                drag.sx.accept(Math.max(0, Math.min(width - drag.w, mouseX - offX)));
                drag.sy.accept(Math.max(0, Math.min(height - drag.h, mouseY - offY)));
            }
        } else if (drag != null) {
            drag = null;
            Config.save();
        }
        wasDown = down;

        for (El e : els) {
            int x = e.gx.getAsInt(), y = e.gy.getAsInt();
            int col = e == drag ? 0xFFFFFF55 : 0xFF55AAFF;
            g.fill(x, y, x + e.w, y + 1, col);
            g.fill(x, y + e.h - 1, x + e.w, y + e.h, col);
            g.fill(x, y, x + 1, y + e.h, col);
            g.fill(x + e.w - 1, y, x + e.w, y + e.h, col);
            g.fill(x + 1, y + 1, x + e.w - 1, y + e.h - 1, 0x3355AAFF);
            g.text(font, e.name, x + 2, y - 10, col);
        }
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
