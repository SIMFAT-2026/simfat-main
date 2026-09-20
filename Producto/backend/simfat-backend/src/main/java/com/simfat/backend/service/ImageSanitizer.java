package com.simfat.backend.service;

import com.simfat.backend.exception.BadRequestException;
import com.simfat.backend.web.SanitizedMultipartFile;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Strips all embedded metadata (EXIF incl. GPS, XMP, IPTC, PNG text chunks, thumbnails) from
 * citizen-report photos.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>The format is detected from magic bytes, never from the client-supplied content type or
 *       file extension. JPEG, PNG and WebP are accepted; anything else (GIF, HEIC, non-images,
 *       corrupt data) is rejected with a 400 instead of being stored unsanitized.</li>
 *   <li>JPEG and PNG are decoded and re-encoded, so no source metadata survives. The EXIF
 *       Orientation tag (1..8) of a JPEG is read and applied to the pixels before re-encoding, so
 *       photos are not displayed sideways once the tag is gone. Pixels are redrawn into an sRGB
 *       raster: an embedded ICC profile is converted to sRGB and then dropped.</li>
 *   <li>WebP (what the web client always uploads) cannot be decoded by the JDK, and no dependency
 *       is added. It is sanitized at the RIFF container level without touching pixel data: only
 *       the image/animation chunks ({@code VP8 }, {@code VP8L}, {@code VP8X}, {@code ALPH},
 *       {@code ANIM}, {@code ANMF}) are kept, every other top-level chunk ({@code EXIF},
 *       {@code XMP }, {@code ICCP}, unknown ones) is dropped, the ICC/EXIF/XMP flag bits of
 *       {@code VP8X} are cleared and the RIFF size is rewritten. Limits: the EXIF orientation of a
 *       WebP is NOT applied (that would require decoding; the browser canvas re-encode already
 *       normalises orientation), and an embedded ICC profile is dropped without converting the
 *       colors. Bytes after the declared RIFF end are discarded. Sub-chunks nested inside
 *       {@code ANMF} frames are copied as they are.</li>
 *   <li>Memory guard: the pixel count is read from the header (JPEG/PNG reader, or the VP8X/VP8L/VP8
 *       header for WebP) and checked against {@code app.upload.image.max-pixels} (default 12 MP)
 *       before any pixel is decoded; input above {@link #MAX_INPUT_BYTES} is rejected too. For
 *       JPEG/PNG the peak heap per request holds the input (up to 10 MB), the decoded source
 *       raster, the redrawn target raster and the encoded output. Both rasters use 4 bytes per
 *       pixel (TYPE_INT_RGB/ARGB), so 12 MP is about 48 MB per raster and roughly 100 MB at peak.
 *       WebP never allocates a raster.</li>
 *   <li>The raw upload is read through a memory-cache image stream, so it never touches the disk
 *       (ImageIO's default disk cache would write the unsanitized bytes, GPS included, to
 *       {@code java.io.tmpdir}).</li>
 * </ul>
 * Tradeoff: JPEG is recompressed (quality 0.92), so there is a small generation loss.
 */
@Component
public class ImageSanitizer {

    /** Same order of magnitude as Spring's default multipart request limit. */
    static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    private static final float JPEG_QUALITY = 0.92f;

    private final long maxPixels;

    public ImageSanitizer(@Value("${app.upload.image.max-pixels:12000000}") int maxPixels) {
        this.maxPixels = maxPixels;
    }

    public MultipartFile sanitize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return file;
        }
        if (file.getSize() > MAX_INPUT_BYTES) {
            throw new BadRequestException("La imagen excede el tamano maximo permitido");
        }
        byte[] input = readBytes(file);
        if (isWebp(input)) {
            return new SanitizedMultipartFile(
                file.getName(),
                withExtension(file.getOriginalFilename(), "webp"),
                "image/webp",
                sanitizeWebp(input)
            );
        }
        boolean jpeg = isJpeg(input);
        if (!jpeg && !isPng(input)) {
            throw new BadRequestException("Formato de imagen no soportado; use JPEG, PNG o WebP");
        }

        BufferedImage source = decode(input);
        BufferedImage oriented = redraw(source, jpeg ? readExifOrientation(input) : 1, !jpeg);
        byte[] output = encode(oriented, jpeg);

        return new SanitizedMultipartFile(
            file.getName(),
            withExtension(file.getOriginalFilename(), jpeg ? "jpg" : "png"),
            jpeg ? "image/jpeg" : "image/png",
            output
        );
    }

    // ---------- decoding ----------

    private BufferedImage decode(byte[] input) {
        // Memory-cache stream on purpose: ImageIO.createImageInputStream(InputStream) honours the global
        // disk-cache setting and would spill the raw, unsanitized upload to java.io.tmpdir.
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new BadRequestException("No fue posible leer la imagen adjunta");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > maxPixels) {
                    throw new BadRequestException("Las dimensiones de la imagen exceden el maximo permitido");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof BadRequestException bad) {
                throw bad;
            }
            throw new BadRequestException("No fue posible leer la imagen adjunta");
        }
    }

    // ---------- orientation + sRGB redraw ----------

    private BufferedImage redraw(BufferedImage source, int orientation, boolean keepAlpha) {
        int w = source.getWidth();
        int h = source.getHeight();
        boolean swap = orientation >= 5 && orientation <= 8;
        int type = keepAlpha && source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h, type);
        Graphics2D g = target.createGraphics();
        try {
            // Nearest neighbour: the transforms are exact 90-degree/flip mappings, no resampling.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(source, transformFor(orientation, w, h), null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** Source-to-destination transform for each EXIF orientation value. */
    private static AffineTransform transformFor(int orientation, int w, int h) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
            default -> new AffineTransform();
        };
    }

    /** JDK-only parse of the JPEG APP1 "Exif" TIFF header for tag 0x0112. Returns 1 when absent or malformed. */
    static int readExifOrientation(byte[] jpeg) {
        int pos = 2;
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) {
                break; // start of scan / end of image: no more metadata segments
            }
            int length = u16(jpeg, pos + 2, true);
            if (length < 2) {
                break;
            }
            int start = pos + 4;
            if (marker == 0xE1 && start + 6 <= jpeg.length
                && jpeg[start] == 'E' && jpeg[start + 1] == 'x' && jpeg[start + 2] == 'i'
                && jpeg[start + 3] == 'f' && jpeg[start + 4] == 0 && jpeg[start + 5] == 0) {
                return orientationFromTiff(jpeg, start + 6, Math.min(jpeg.length, pos + 2 + length));
            }
            pos += 2 + length;
        }
        return 1;
    }

    private static int orientationFromTiff(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) {
            return 1;
        }
        boolean bigEndian;
        if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
            bigEndian = true;
        } else if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
            bigEndian = false;
        } else {
            return 1;
        }
        long ifd = u32(b, tiff + 4, bigEndian);
        int entries = ifd + 2 <= end - tiff ? u16(b, tiff + (int) ifd, bigEndian) : 0;
        for (int i = 0; i < entries; i++) {
            int entry = tiff + (int) ifd + 2 + i * 12;
            if (entry + 12 > end) {
                break;
            }
            if (u16(b, entry, bigEndian) == 0x0112) {
                int value = u16(b, entry + 8, bigEndian);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int at, boolean bigEndian) {
        int hi = b[at] & 0xFF;
        int lo = b[at + 1] & 0xFF;
        return bigEndian ? (hi << 8) | lo : (lo << 8) | hi;
    }

    private static long u32(byte[] b, int at, boolean bigEndian) {
        long a = u16(b, at, bigEndian);
        long c = u16(b, at + 2, bigEndian);
        return bigEndian ? (a << 16) | c : (c << 16) | a;
    }

    // ---------- WebP (RIFF container, metadata stripped without decoding) ----------

    private static final Set<String> WEBP_KEPT_CHUNKS = Set.of("VP8 ", "VP8L", "VP8X", "ALPH", "ANIM", "ANMF");
    private static final int VP8X_FLAG_MASK_METADATA = 0x20 | 0x08 | 0x04; // ICC, EXIF, XMP

    private byte[] sanitizeWebp(byte[] in) {
        // RIFF header: "RIFF" <size:u32le> "WEBP". The declared size must fit inside the input.
        long riffSize = u32le(in, 4);
        long riffEnd = 8 + riffSize;
        if (riffSize < 4 + 8 || riffEnd > in.length) {
            throw unreadable();
        }
        int end = (int) riffEnd;

        // Dimensions come from the first chunk header and are checked before anything else is parsed.
        checkPixelLimit(webpDimensions(in, 12, end, true));

        ByteArrayOutputStream body = new ByteArrayOutputStream(end);
        boolean imageData = false;
        int pos = 12;
        while (pos < end) {
            if (pos + 8 > end) {
                throw unreadable();
            }
            String fourcc = new String(in, pos, 4, StandardCharsets.ISO_8859_1);
            long size = u32le(in, pos + 4);
            long payloadEnd = pos + 8L + size;
            if (payloadEnd > end) {
                throw unreadable();
            }
            int payload = pos + 8;
            int length = (int) size;
            // Chunks are padded to even size; tolerate a missing pad byte on the very last chunk only.
            long next = payloadEnd + (size & 1);
            if (next > end && !(next == end + 1 && payloadEnd == end)) {
                throw unreadable();
            }
            if (WEBP_KEPT_CHUNKS.contains(fourcc)) {
                if ("VP8 ".equals(fourcc) || "VP8L".equals(fourcc) || "ANMF".equals(fourcc)) {
                    imageData = true;
                }
                if ("VP8 ".equals(fourcc) || "VP8L".equals(fourcc)) {
                    checkPixelLimit(webpDimensions(in, pos, end, false));
                }
                byte[] copy = Arrays.copyOfRange(in, payload, payload + length);
                if ("VP8X".equals(fourcc)) {
                    if (length < 10) {
                        throw unreadable();
                    }
                    copy[0] = (byte) (copy[0] & ~VP8X_FLAG_MASK_METADATA);
                }
                body.writeBytes(fourcc.getBytes(StandardCharsets.ISO_8859_1));
                writeU32le(body, size);
                body.writeBytes(copy);
                if ((length & 1) == 1) {
                    body.write(0);
                }
            }
            pos = (int) Math.min(next, end);
        }
        if (!imageData) {
            throw unreadable();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(body.size() + 12);
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeU32le(out, 4L + body.size());
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    /**
     * Reads width x height from the chunk header at {@code pos} (VP8X canvas, VP8L or VP8 key frame).
     * When {@code first} is true the chunk must be one of those three; otherwise other chunks yield 0.
     */
    private static long webpDimensions(byte[] in, int pos, int end, boolean first) {
        if (pos + 8 > end) {
            throw unreadable();
        }
        String fourcc = new String(in, pos, 4, StandardCharsets.ISO_8859_1);
        long size = u32le(in, pos + 4);
        int p = pos + 8;
        if (p + size > end) {
            throw unreadable();
        }
        switch (fourcc) {
            case "VP8X" -> {
                if (size < 10) {
                    throw unreadable();
                }
                return (u24le(in, p + 4) + 1L) * (u24le(in, p + 7) + 1L);
            }
            case "VP8L" -> {
                // Signature 0x2F, then 14 bits (width - 1) and 14 bits (height - 1), little-endian bit order.
                if (size < 5 || (in[p] & 0xFF) != 0x2F) {
                    throw unreadable();
                }
                long bits = (in[p + 1] & 0xFFL) | ((in[p + 2] & 0xFFL) << 8)
                    | ((in[p + 3] & 0xFFL) << 16) | ((in[p + 4] & 0xFFL) << 24);
                return ((bits & 0x3FFF) + 1) * (((bits >> 14) & 0x3FFF) + 1);
            }
            case "VP8 " -> {
                // Key frame: 3-byte frame tag (bit 0 = 0), start code 9D 01 2A, 14-bit width and height.
                if (size < 10 || (in[p] & 1) != 0
                    || (in[p + 3] & 0xFF) != 0x9D || (in[p + 4] & 0xFF) != 0x01 || (in[p + 5] & 0xFF) != 0x2A) {
                    throw unreadable();
                }
                long w = ((in[p + 6] & 0xFF) | ((in[p + 7] & 0xFF) << 8)) & 0x3FFF;
                long h = ((in[p + 8] & 0xFF) | ((in[p + 9] & 0xFF) << 8)) & 0x3FFF;
                return w * h;
            }
            default -> {
                if (first) {
                    throw unreadable();
                }
                return 0;
            }
        }
    }

    private void checkPixelLimit(long pixels) {
        if (pixels > maxPixels) {
            throw new BadRequestException("Las dimensiones de la imagen exceden el maximo permitido");
        }
    }

    private static BadRequestException unreadable() {
        return new BadRequestException("No fue posible leer la imagen adjunta");
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static long u32le(byte[] b, int at) {
        return (b[at] & 0xFFL) | ((b[at + 1] & 0xFFL) << 8) | ((b[at + 2] & 0xFFL) << 16) | ((b[at + 3] & 0xFFL) << 24);
    }

    private static int u24le(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) | ((b[at + 2] & 0xFF) << 16);
    }

    private static void writeU32le(ByteArrayOutputStream out, long v) {
        for (int i = 0; i < 4; i++) {
            out.write((int) ((v >> (8 * i)) & 0xFF));
        }
    }

    // ---------- encoding ----------

    private byte[] encode(BufferedImage image, boolean jpeg) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(jpeg ? "jpeg" : "png").next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (jpeg) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.setOutput(stream);
            // Null metadata: the writer emits only the mandatory structure, nothing from the source.
            writer.write(null, new IIOImage(image, null, null), param);
            stream.flush();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BadRequestException("No fue posible procesar la imagen adjunta");
        } finally {
            writer.dispose();
        }
    }

    // ---------- helpers ----------

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("No fue posible leer el archivo adjunto");
        }
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }

    private static String withExtension(String originalFilename, String extension) {
        String base = originalFilename == null || originalFilename.isBlank() ? "photo" : originalFilename;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return (base.isBlank() ? "photo" : base) + "." + extension;
    }
}
