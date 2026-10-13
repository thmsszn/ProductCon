package de.localai.baumeister.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.localai.baumeister.arch.Architect;
import de.localai.baumeister.arch.B;
import de.localai.baumeister.arch.BuildingSpec;
import de.localai.baumeister.arch.Voxels;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Entwicklerwerkzeug (ohne Minecraft): erzeugt Gebäude aus Entwürfen und rendert sie als isometrische Bilder
 * (vorne-links, hinten-rechts) plus Grundriss-Schnitte. Zusätzlich ein Fuzz-Test über viele Größen/Entwürfe.
 *
 * <pre>java Preview &lt;ausgabeordner&gt; [specs.jsonl] [--fuzz N]</pre>
 */
public final class Preview {
    private static final int S = 14;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "preview-out");
        Files.createDirectories(out);
        int fuzz = 0;
        List<String[]> cases = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--fuzz")) { fuzz = Integer.parseInt(args[++i]); continue; }
            for (String line : Files.readAllLines(Path.of(args[i]))) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // Format: name|W|H|D|{json}
                cases.add(line.split("\\|", 5));
            }
        }
        for (String[] c : cases) {
            String name = c[0];
            int w = Integer.parseInt(c[1]), h = Integer.parseInt(c[2]), d = Integer.parseInt(c[3]);
            JsonObject o = JsonParser.parseString(c[4]).getAsJsonObject();
            BuildingSpec spec = BuildingSpec.fromJson(o);
            long t0 = System.nanoTime();
            Voxels vox = Architect.generate(spec, w, h, d, b -> true);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            render(vox, out.resolve(name + "-front.png").toFile(), name + " (" + w + "x" + h + "x" + d + ")");
            render(vox.rotated180(), out.resolve(name + "-back.png").toFile(), name + " von hinten");
            plans(vox, out.resolve(name + "-plan.png").toFile());
            System.out.printf(Locale.ROOT, "%-24s %3dx%3dx%3d  %6d Bloecke  %4d ms%n", name, w, h, d, vox.countBlocks(), ms);
        }
        if (fuzz > 0) fuzz(fuzz);
    }

    /** Viele zufällige Größen/Entwürfe: darf nie abstürzen und muss im Bauplatz bleiben. */
    private static void fuzz(int n) {
        Random r = new Random(42);
        String[] types = {"house", "cottage", "mansion", "tavern", "barn", "modern_house", "tower", "lighthouse", "castle", "temple", "church", "custom"};
        String[] styles = {"medieval", "rustic", "nordic", "fantasy", "victorian", "japanese", "desert", "modern", "classical", "stone", "viking", "gothic", "steampunk", "oriental"};
        String[] roofs = {"gable", "hip", "flat", "cone"};
        int failures = 0;
        for (int i = 0; i < n; i++) {
            int w = 3 + r.nextInt(62), h = 3 + r.nextInt(60), d = 3 + r.nextInt(62);
            JsonObject o = new JsonObject();
            o.addProperty("summary", "fuzz " + i);
            o.addProperty("type", types[r.nextInt(types.length)]);
            o.addProperty("style", styles[r.nextInt(styles.length)]);
            o.addProperty("floors", 1 + r.nextInt(6));
            o.addProperty("roof", roofs[r.nextInt(roofs.length)]);
            o.add("features", JsonParser.parseString("[\"chimney\",\"porch\",\"balcony\",\"garden\",\"fence\",\"path\",\"shutters\",\"flower_boxes\",\"lanterns\",\"trees\",\"pool\",\"interior\"]"));
            try {
                Voxels v = Architect.generate(BuildingSpec.fromJson(o), w, h, d, b -> true);
                if (v.w != w || v.h != h || v.d != d) throw new IllegalStateException("Groesse falsch");
                if (v.countBlocks() == 0) throw new IllegalStateException("leer");
            } catch (Exception e) {
                failures++;
                System.out.println("FUZZ-FEHLER " + o + " " + w + "x" + h + "x" + d + ": " + e);
                e.printStackTrace(System.out);
            }
        }
        System.out.println("Fuzz: " + n + " Faelle, " + failures + " Fehler");
        if (failures > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ Isometrisches Rendering

    static void render(Voxels v, File file, String title) throws Exception {
        double c = Math.cos(Math.toRadians(30)) * S, sv = 0.5 * S;
        int width = (int) ((v.w + v.d) * c) + 40;
        int height = (int) ((v.w + v.d) * sv + v.h * S) + 70;
        double ox = v.d * c + 20, oy = (v.w + v.d) * sv + v.h * S + 50;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xBFD9EE));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title, 10, 18);
        for (int sum = v.w + v.d - 2; sum >= 0; sum--) {
            for (int y = 0; y < v.h; y++) {
                for (int x = Math.max(0, sum - (v.d - 1)); x <= Math.min(v.w - 1, sum); x++) {
                    int z = sum - x;
                    String st = v.get(x, y, z);
                    if (B.isAir(st)) continue;
                    drawBlock(g, v, x, y, z, st, c, sv, ox, oy);
                }
            }
        }
        g.dispose();
        ImageIO.write(img, "png", file);
    }

    private static int[] proj(double x, double y, double z, double c, double sv, double ox, double oy) {
        return new int[] {(int) Math.round(ox + (x - z) * c), (int) Math.round(oy - (x + z) * sv - y * S)};
    }

    private static void drawBlock(Graphics2D g, Voxels v, int x, int y, int z, String st, double c, double sv, double ox, double oy) {
        String n = B.name(st);
        double x0 = x, x1 = x + 1, z0 = z, z1 = z + 1, y0 = y, y1 = y + 1;
        boolean thin = B.isThin(st);
        if (n.endsWith("_slab")) {
            if (st.contains("type=top")) y0 = y + 0.5; else if (!st.contains("type=double")) y1 = y + 0.5;
        } else if (n.endsWith("_carpet") || n.endsWith("pressure_plate")) {
            y1 = y + 0.08;
        } else if (n.endsWith("_trapdoor")) {
            if (st.contains("open=true")) {
                double t = 0.18;
                if (st.contains("facing=north")) { z0 = z + 1 - t; }
                else if (st.contains("facing=south")) { z1 = z + t; }
                else if (st.contains("facing=west")) { x0 = x + 1 - t; }
                else { x1 = x + t; }
            } else if (st.contains("half=top")) y0 = y + 0.8; else y1 = y + 0.2;
        } else if (thin) {
            double m = n.endsWith("_pane") || n.endsWith("_fence") || n.endsWith("_wall") || n.endsWith("_bars") ? 0.35
                : n.endsWith("_door") || n.equals("ladder") ? 0.4 : 0.3;
            x0 = x + m; x1 = x + 1 - m; z0 = z + m; z1 = z + 1 - m;
            if (B.isPlant(n) || n.contains("torch") || n.contains("lantern") || n.startsWith("potted") || n.endsWith("campfire")) {
                y1 = y + (n.contains("lantern") ? 0.55 : n.endsWith("campfire") ? 0.45 : 0.7);
                if (n.contains("lantern") && st.contains("hanging=true")) { y0 = y + 0.3; y1 = y + 0.9; }
            }
            if (n.endsWith("_bed")) { x0 = x; x1 = x + 1; z0 = z; z1 = z + 1; y1 = y + 0.56; }
        }
        Color base = color(n, st);
        boolean glass = n.contains("glass") || n.equals("water");
        if (glass) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        boolean fullCube = !thin && x0 == x && x1 == x + 1 && z0 == z && z1 == z + 1 && y0 == y && y1 == y + 1;
        // Oberseite
        if (!fullCube || !v.isSolid(x, y + 1, z)) {
            face(g, base, 1.0, proj(x0, y1, z0, c, sv, ox, oy), proj(x1, y1, z0, c, sv, ox, oy), proj(x1, y1, z1, c, sv, ox, oy), proj(x0, y1, z1, c, sv, ox, oy));
        }
        // Nordseite (vorne, -z)
        if (!fullCube || !v.isSolid(x, y, z - 1)) {
            face(g, base, 0.80, proj(x0, y0, z0, c, sv, ox, oy), proj(x1, y0, z0, c, sv, ox, oy), proj(x1, y1, z0, c, sv, ox, oy), proj(x0, y1, z0, c, sv, ox, oy));
        }
        // Westseite (-x)
        if (!fullCube || !v.isSolid(x - 1, y, z)) {
            face(g, base, 0.62, proj(x0, y0, z0, c, sv, ox, oy), proj(x0, y0, z1, c, sv, ox, oy), proj(x0, y1, z1, c, sv, ox, oy), proj(x0, y1, z0, c, sv, ox, oy));
        }
        if (glass) g.setComposite(AlphaComposite.SrcOver);
        if (n.endsWith("_stairs")) stairHint(g, st, x, y, z, c, sv, ox, oy);
    }

    /** Treppen: Stufenkante andeuten, damit Dachrichtungen erkennbar sind. */
    private static void stairHint(Graphics2D g, String st, int x, int y, int z, double c, double sv, double ox, double oy) {
        g.setColor(new Color(0, 0, 0, 90));
        g.setStroke(new BasicStroke(1.2f));
        double yy = st.contains("half=top") ? y + 0.5 : y + 1;
        int[] a, b;
        if (st.contains("facing=north") || st.contains("facing=south")) {
            double zz = z + 0.5;
            a = proj(x, yy, zz, c, sv, ox, oy); b = proj(x + 1, yy, zz, c, sv, ox, oy);
        } else {
            double xx = x + 0.5;
            a = proj(xx, yy, z, c, sv, ox, oy); b = proj(xx, yy, z + 1, c, sv, ox, oy);
        }
        g.drawLine(a[0], a[1], b[0], b[1]);
    }

    private static void face(Graphics2D g, Color base, double shade, int[]... pts) {
        Polygon poly = new Polygon();
        for (int[] p : pts) poly.addPoint(p[0], p[1]);
        g.setColor(new Color(clamp(base.getRed() * shade), clamp(base.getGreen() * shade), clamp(base.getBlue() * shade)));
        g.fillPolygon(poly);
        g.setColor(new Color(0, 0, 0, 35));
        g.drawPolygon(poly);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    // ------------------------------------------------------------------ Grundrisse

    static void plans(Voxels v, File file) throws Exception {
        int cell = 9;
        int maxY = Math.min(v.h - 1, 24);
        int cols = 4;
        int rows = (maxY + cols - 1) / cols;
        int pw = v.w * cell + 12, ph = v.d * cell + 24;
        BufferedImage img = new BufferedImage(cols * pw, rows * ph, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int y = 1; y <= maxY; y++) {
            int i = y - 1;
            int px = (i % cols) * pw + 6, py = (i / cols) * ph + 16;
            g.setColor(Color.BLACK);
            g.drawString("y=" + y + " (vorne = oben)", px, py - 4);
            for (int x = 0; x < v.w; x++)
                for (int z = 0; z < v.d; z++) {
                    String st = v.get(x, y, z);
                    Color col = B.isAir(st) ? new Color(245, 245, 245) : color(B.name(st), st);
                    g.setColor(col);
                    g.fillRect(px + x * cell, py + z * cell, cell - 1, cell - 1);
                    if (!B.isAir(st) && B.isThin(st)) {
                        g.setColor(Color.BLACK);
                        g.drawRect(px + x * cell + 2, py + z * cell + 2, cell - 5, cell - 5);
                    }
                }
        }
        g.dispose();
        ImageIO.write(img, "png", file);
    }

    // ------------------------------------------------------------------ Farben

    static Color color(String n, String st) {
        if (n.contains("glass")) return n.contains("white") ? new Color(230, 235, 240) : n.contains("light_blue") ? new Color(140, 190, 230) : new Color(175, 215, 235);
        if (n.endsWith("leaves")) return n.startsWith("cherry") ? new Color(235, 170, 200) : n.contains("azalea") ? new Color(95, 140, 50) : new Color(60, 130, 45);
        if (n.equals("grass_block") || n.equals("moss_block") || n.equals("short_grass") || n.equals("fern")) return new Color(95, 160, 65);
        if (n.equals("water") || n.equals("water_cauldron")) return new Color(50, 90, 200);
        if (n.contains("lantern") || n.contains("torch") || n.equals("glowstone") || n.equals("shroomlight") || n.endsWith("campfire")) return new Color(255, 205, 80);
        if (n.equals("dirt_path") || n.equals("dirt") || n.equals("coarse_dirt")) return new Color(150, 115, 65);
        if (n.equals("gravel")) return new Color(135, 128, 122);
        if (n.equals("sand")) return new Color(225, 212, 165);
        if (n.endsWith("_bed")) return bedColor(n);
        if (n.equals("hay_block")) return new Color(200, 170, 40);
        if (n.equals("bookshelf")) return new Color(130, 95, 60);
        if (n.equals("chest") || n.equals("barrel")) return new Color(150, 105, 55);
        if (n.endsWith("_carpet")) return dye(n, new Color(170, 60, 60));
        if (B.isPlant(n) || n.startsWith("potted_")) return new Color(220, 70, 90);
        if (n.contains("dark_oak")) return new Color(70, 46, 24);
        if (n.contains("spruce")) return new Color(112, 82, 48);
        if (n.contains("birch")) return new Color(212, 200, 140);
        if (n.contains("cherry")) return new Color(225, 175, 170);
        if (n.contains("acacia")) return new Color(170, 92, 52);
        if (n.contains("jungle")) return new Color(160, 115, 78);
        if (n.contains("mangrove")) return new Color(118, 54, 48);
        if (n.contains("pale_oak")) return new Color(225, 215, 205);
        if (n.contains("oak")) return new Color(165, 132, 80);
        if (n.contains("mossy")) return new Color(105, 125, 92);
        if (n.contains("cracked")) return new Color(112, 112, 112);
        if (n.contains("deepslate")) return new Color(72, 72, 78);
        if (n.contains("blackstone")) return new Color(45, 40, 48);
        if (n.contains("stone_brick") || n.equals("chiseled_stone_bricks") || n.equals("stone") || n.equals("stone_stairs")) return new Color(125, 125, 125);
        if (n.contains("cobble")) return new Color(112, 112, 112);
        if (n.contains("mud_brick") || n.equals("packed_mud")) return new Color(140, 105, 80);
        if (n.contains("nether_brick")) return new Color(60, 30, 36);
        if (n.contains("brick")) return new Color(152, 76, 60);
        if (n.contains("quartz") || n.equals("calcite")) return new Color(236, 232, 226);
        if (n.contains("white_terracotta")) return new Color(210, 180, 162);
        if (n.endsWith("terracotta")) return dye(n, new Color(152, 94, 67));
        if (n.endsWith("concrete")) return dye(n, new Color(200, 200, 200));
        if (n.contains("sandstone")) return new Color(220, 206, 160);
        if (n.contains("smooth_stone")) return new Color(160, 160, 160);
        if (n.contains("andesite")) return new Color(138, 138, 140);
        if (n.contains("diorite")) return new Color(205, 205, 205);
        if (n.contains("granite")) return new Color(155, 105, 85);
        if (n.contains("prismarine")) return n.contains("dark") ? new Color(50, 92, 75) : new Color(95, 160, 150);
        if (n.contains("purpur")) return new Color(170, 125, 170);
        if (n.contains("copper")) return n.contains("oxidized") ? new Color(80, 160, 130) : new Color(190, 110, 80);
        if (n.contains("amethyst")) return new Color(140, 100, 190);
        if (n.contains("tuff")) return new Color(110, 112, 105);
        if (n.contains("basalt")) return new Color(80, 80, 85);
        if (n.contains("furnace") || n.contains("smoker") || n.equals("stonecutter") || n.equals("anvil") || n.equals("grindstone")) return new Color(100, 100, 100);
        if (n.contains("crafting") || n.contains("table") || n.equals("lectern") || n.equals("loom")) return new Color(140, 100, 60);
        if (n.contains("iron") || n.equals("ladder")) return n.equals("ladder") ? new Color(150, 115, 70) : new Color(190, 190, 190);
        if (n.contains("sea_lantern")) return new Color(200, 230, 225);
        if (n.contains("bell")) return new Color(230, 190, 50);
        if (n.contains("banner")) return dye(n, new Color(180, 40, 40));
        if (n.contains("candle")) return new Color(240, 230, 200);
        return new Color(255, 0, 255);
    }

    private static Color bedColor(String n) {
        return dye(n, new Color(170, 40, 40));
    }

    private static Color dye(String n, Color fallback) {
        if (n.startsWith("white")) return new Color(235, 235, 235);
        if (n.startsWith("light_gray")) return new Color(150, 150, 145);
        if (n.startsWith("gray")) return new Color(60, 64, 68);
        if (n.startsWith("black")) return new Color(22, 22, 28);
        if (n.startsWith("red")) return new Color(160, 40, 40);
        if (n.startsWith("orange")) return new Color(220, 110, 30);
        if (n.startsWith("yellow")) return new Color(235, 190, 40);
        if (n.startsWith("lime")) return new Color(110, 180, 40);
        if (n.startsWith("green")) return new Color(80, 110, 40);
        if (n.startsWith("cyan")) return new Color(30, 130, 140);
        if (n.startsWith("light_blue")) return new Color(70, 170, 220);
        if (n.startsWith("blue")) return new Color(50, 60, 160);
        if (n.startsWith("purple")) return new Color(120, 45, 170);
        if (n.startsWith("magenta")) return new Color(180, 60, 170);
        if (n.startsWith("pink")) return new Color(230, 130, 160);
        if (n.startsWith("brown")) return new Color(110, 75, 45);
        return fallback;
    }
}
