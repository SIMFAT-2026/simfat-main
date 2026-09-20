package com.simfat.backend.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-assembled WebP containers for tests (the JDK cannot encode or decode WebP). Payloads are
 * structurally valid at the RIFF level; the sanitizer never decodes pixels, so the VP8/VP8L
 * bitstreams only need a correct header.
 */
public final class WebpTestSupport {

    public static final int FLAG_ANIMATION = 0x02;
    public static final int FLAG_XMP = 0x04;
    public static final int FLAG_EXIF = 0x08;
    public static final int FLAG_ALPHA = 0x10;
    public static final int FLAG_ICC = 0x20;

    /** Tail of the well-known 1x1 lossless WebP bitstream (after the 5-byte VP8L header). */
    private static final byte[] VP8L_TAIL = {0x10, 0x07, 0x10, 0x11, 0x11, (byte) 0x88, (byte) 0x88, (byte) 0xFE, 0x07};

    private WebpTestSupport() {
    }

    public record Chunk(String fourcc, byte[] payload) {
    }

    public static Chunk chunk(String fourcc, byte[] payload) {
        return new Chunk(fourcc, payload);
    }

    public static Chunk chunk(String fourcc, String payload) {
        return new Chunk(fourcc, payload.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** VP8L payload declaring the given dimensions (14 bits each, stored minus one). */
    public static byte[] vp8lPayload(int width, int height) {
        long bits = (width - 1L) | ((height - 1L) << 14);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x2F);
        for (int i = 0; i < 4; i++) {
            out.write((int) ((bits >> (8 * i)) & 0xFF));
        }
        out.writeBytes(VP8L_TAIL);
        return out.toByteArray();
    }

    /** VP8 lossy key frame header: frame tag, start code, 14-bit width and height. */
    public static byte[] vp8Payload(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0x10, 0x02, 0x00, (byte) 0x9D, 0x01, 0x2A});
        out.write(width & 0xFF);
        out.write((width >> 8) & 0x3F);
        out.write(height & 0xFF);
        out.write((height >> 8) & 0x3F);
        out.writeBytes(new byte[] {0x01, 0x02, 0x03});
        return out.toByteArray();
    }

    public static byte[] vp8xPayload(int flags, int canvasWidth, int canvasHeight) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(flags);
        out.writeBytes(new byte[] {0, 0, 0});
        writeU24(out, canvasWidth - 1);
        writeU24(out, canvasHeight - 1);
        return out.toByteArray();
    }

    public static byte[] exifPayload() {
        return "Exif\0\0MM\0*GPSLatitudeRef=S GPSLatitude=36.8201 GPSLongitude=73.0444".getBytes(StandardCharsets.ISO_8859_1);
    }

    public static byte[] xmpPayload() {
        return "<x:xmpmeta><rdf:li>GPSLatitude=36.8201</rdf:li></x:xmpmeta>".getBytes(StandardCharsets.ISO_8859_1);
    }

    /** RIFF container with a correct size field. */
    public static byte[] riff(Chunk... chunks) {
        byte[] body = body(chunks);
        return wrap(4 + body.length, body);
    }

    /** RIFF container with an arbitrary (possibly wrong) declared size. */
    public static byte[] riffWithDeclaredSize(long declaredSize, Chunk... chunks) {
        return wrap(declaredSize, body(chunks));
    }

    /** Same as {@link #riff} but the chunk at {@code index} declares {@code declaredSize} instead of its real size. */
    public static byte[] riffWithChunkSize(int index, long declaredSize, Chunk... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int i = 0; i < chunks.length; i++) {
            writeChunk(body, chunks[i], i == index ? declaredSize : chunks[i].payload().length);
        }
        return wrap(4 + body.size(), body.toByteArray());
    }

    /** Minimal valid still image: VP8L only. */
    public static byte[] simpleLossless(int width, int height) {
        return riff(chunk("VP8L", vp8lPayload(width, height)));
    }

    public static List<String> chunkTypes(byte[] webp) {
        List<String> types = new ArrayList<>();
        int pos = 12;
        while (pos + 8 <= webp.length) {
            types.add(new String(webp, pos, 4, StandardCharsets.ISO_8859_1));
            long size = u32(webp, pos + 4);
            pos += 8 + (int) size + (int) (size & 1);
        }
        return types;
    }

    /** Payload of the first chunk with the given FourCC, or null. */
    public static byte[] chunkPayload(byte[] webp, String fourcc) {
        int pos = 12;
        while (pos + 8 <= webp.length) {
            long size = u32(webp, pos + 4);
            if (new String(webp, pos, 4, StandardCharsets.ISO_8859_1).equals(fourcc)) {
                byte[] payload = new byte[(int) size];
                System.arraycopy(webp, pos + 8, payload, 0, (int) size);
                return payload;
            }
            pos += 8 + (int) size + (int) (size & 1);
        }
        return null;
    }

    public static long declaredRiffSize(byte[] webp) {
        return u32(webp, 4);
    }

    // ---------- internals ----------

    private static byte[] body(Chunk... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Chunk c : chunks) {
            writeChunk(body, c, c.payload().length);
        }
        return body.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, Chunk c, long declaredSize) {
        out.writeBytes(c.fourcc().getBytes(StandardCharsets.US_ASCII));
        writeU32(out, declaredSize);
        out.writeBytes(c.payload());
        if ((c.payload().length & 1) == 1) {
            out.write(0);
        }
    }

    private static byte[] wrap(long riffSize, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeU32(out, riffSize);
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static void writeU32(ByteArrayOutputStream out, long v) {
        for (int i = 0; i < 4; i++) {
            out.write((int) ((v >> (8 * i)) & 0xFF));
        }
    }

    private static void writeU24(ByteArrayOutputStream out, int v) {
        for (int i = 0; i < 3; i++) {
            out.write((v >> (8 * i)) & 0xFF);
        }
    }

    private static long u32(byte[] b, int at) {
        return (b[at] & 0xFFL) | ((b[at + 1] & 0xFFL) << 8) | ((b[at + 2] & 0xFFL) << 16) | ((b[at + 3] & 0xFFL) << 24);
    }
}
