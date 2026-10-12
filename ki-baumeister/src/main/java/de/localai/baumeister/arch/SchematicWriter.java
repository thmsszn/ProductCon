package de.localai.baumeister.arch;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Schreibt ein Voxel-Raster als Sponge-Schematic Version 2 ({@code .schem}), lesbar von WorldEdit,
 * FastAsyncWorldEdit und Litematica. Leere Zellen werden als Luft gespeichert.
 */
public final class SchematicWriter {
    private static final byte TAG_END = 0, TAG_SHORT = 2, TAG_INT = 3, TAG_BYTE_ARRAY = 7, TAG_STRING = 8,
        TAG_LIST = 9, TAG_COMPOUND = 10, TAG_INT_ARRAY = 11;

    private SchematicWriter() {}

    public static void write(Voxels v, int dataVersion, String name, OutputStream target) throws IOException {
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put(B.AIR, 0);
        ByteArrayOutputStream blocks = new ByteArrayOutputStream(v.w * v.h * v.d);
        // Reihenfolge laut Spezifikation: index = x + z * Width + y * Width * Length
        for (int y = 0; y < v.h; y++)
            for (int z = 0; z < v.d; z++)
                for (int x = 0; x < v.w; x++) {
                    String s = v.get(x, y, z);
                    if (s == null) s = B.AIR;
                    Integer id = palette.get(s);
                    if (id == null) {
                        id = palette.size();
                        palette.put(s, id);
                    }
                    writeVarInt(blocks, id);
                }
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(target))) {
            out.writeByte(TAG_COMPOUND);
            out.writeUTF("Schematic");
            intTag(out, "Version", 2);
            intTag(out, "DataVersion", dataVersion);
            shortTag(out, "Width", v.w);
            shortTag(out, "Height", v.h);
            shortTag(out, "Length", v.d);
            out.writeByte(TAG_INT_ARRAY);
            out.writeUTF("Offset");
            out.writeInt(3);
            out.writeInt(0);
            out.writeInt(0);
            out.writeInt(0);
            intTag(out, "PaletteMax", palette.size());
            out.writeByte(TAG_COMPOUND);
            out.writeUTF("Palette");
            for (Map.Entry<String, Integer> e : palette.entrySet()) intTag(out, e.getKey(), e.getValue());
            out.writeByte(TAG_END);
            byte[] data = blocks.toByteArray();
            out.writeByte(TAG_BYTE_ARRAY);
            out.writeUTF("BlockData");
            out.writeInt(data.length);
            out.write(data);
            out.writeByte(TAG_LIST);
            out.writeUTF("BlockEntities");
            out.writeByte(TAG_COMPOUND);
            out.writeInt(0);
            out.writeByte(TAG_COMPOUND);
            out.writeUTF("Metadata");
            out.writeByte(TAG_STRING);
            out.writeUTF("Name");
            writeString(out, name);
            out.writeByte(TAG_STRING);
            out.writeUTF("Author");
            writeString(out, "KI-Baumeister");
            out.writeByte(TAG_END);
            out.writeByte(TAG_END);
        }
    }

    private static void intTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(TAG_INT);
        out.writeUTF(name);
        out.writeInt(value);
    }

    private static void shortTag(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(TAG_SHORT);
        out.writeUTF(name);
        out.writeShort(value);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        out.writeUTF(value.length() > 200 ? value.substring(0, 200) : value);
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
