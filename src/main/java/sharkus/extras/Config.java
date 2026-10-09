package sharkus.extras;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("sharkus-extras.json");
    public static Config I = new Config();

    public boolean armorDisplay, healthIndicator, hitColor, nametags, blockOutlines, teamTracker,
            itemPhysics, cps, fps = true, ping, crosshairIndicator, shieldStatus, motionBlur;

    // Nametags
    public String ownNameOverride = "";
    public java.util.Map<String, String> nameReplacements = new java.util.HashMap<>();

    // Farben
    public int hitRgb = 0xFF0000;
    public int hitAlpha = 50;          // 0-100 Prozent
    public int outlineRgb = 0x00FFFF;
    public int outlineThickness = 2;   // 1-4
    public String teamNames = "";

    // Stile
    public int healthStyle = 0;        // 0 Herzen, 1 Kompakt (Herz + Zahl), 2 Leiste
    public int crosshairStyle = 0;     // 0 Kreuz, 1 Punkt, 2 Ring, 3 Ecken, 4 X
    public boolean crosshairBar = true;

    // HUD-Positionen (GUI-Pixel von links oben), -1 = automatisch
    public int fpsX = 4, fpsY = 4;
    public int cpsX = 4, cpsY = 14;
    public int pingX = 4, pingY = 24;
    public int armorX = 4, armorY = 40;
    public int shieldX = -1, shieldY = -1;

    public void resetPositions() {
        fpsX = 4; fpsY = 4;
        cpsX = 4; cpsY = 14;
        pingX = 4; pingY = 24;
        armorX = 4; armorY = 40;
        shieldX = -1; shieldY = -1;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config loaded = GSON.fromJson(Files.readString(FILE), Config.class);
                if (loaded != null) I = loaded;
            }
        } catch (Exception e) { e.printStackTrace(); }
        if (I.nameReplacements == null) I.nameReplacements = new java.util.HashMap<>();
        if (I.teamNames == null) I.teamNames = "";
        if (I.ownNameOverride == null) I.ownNameOverride = "";
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(I)); } catch (Exception e) { e.printStackTrace(); }
    }
}
