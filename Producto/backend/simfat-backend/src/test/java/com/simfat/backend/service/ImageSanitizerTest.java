package com.simfat.backend.service;

import static com.simfat.backend.service.WebpTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.simfat.backend.exception.BadRequestException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

public class ImageSanitizerTest {

    static final int W = 48;
    static final int H = 32;

    private final ImageSanitizer sanitizer = new ImageSanitizer(12_000_000);

    // ---------- fixtures (shared with the controller-level test) ----------

    /** White W x H image: red 16x16 block at the top-left, blue 16x16 block right next to it. */
    static BufferedImage markerImage(int type) {
        BufferedImage img = new BufferedImage(W, H, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.RED);
        g.fillRect(0, 0, 16, 16);
        g.setColor(Color.BLUE);
        g.fillRect(16, 0, 16, 16);
        g.dispose();
        return img;
    }

    static byte[] encode(BufferedImage img, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** TIFF block with an Orientation tag plus GPS-like ASCII bytes, wrapped as an APP1 "Exif" segment. */
    static byte[] app1Exif(int orientation, boolean bigEndian) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes((bigEndian ? "MM" : "II").getBytes(StandardCharsets.US_ASCII));
        tiff.writeBytes(u16(42, bigEndian));
        tiff.writeBytes(u32(8, bigEndian));
        tiff.writeBytes(u16(1, bigEndian)); // one IFD0 entry
        tiff.writeBytes(u16(0x0112, bigEndian)); // Orientation
        tiff.writeBytes(u16(3, bigEndian)); // SHORT
        tiff.writeBytes(u32(1, bigEndian));
        tiff.writeBytes(u16(orientation, bigEndian));
        tiff.writeBytes(new byte[] {0, 0});
        tiff.writeBytes(u32(0, bigEndian)); // no next IFD
        tiff.writeBytes("GPSLatitudeRef=S GPSLatitude=36.8201 GPSLongitude=73.0444".getBytes(StandardCharsets.US_ASCII));

        byte[] header = {'E', 'x', 'i', 'f', 0, 0};
        int length = 2 + header.length + tiff.size();
        ByteArrayOutputStream seg = new ByteArrayOutputStream();
        seg.write(0xFF);
        seg.write(0xE1);
        seg.write((length >> 8) & 0xFF);
        seg.write(length & 0xFF);
        seg.writeBytes(header);
        seg.writeBytes(tiff.toByteArray());
        return seg.toByteArray();
    }

    private static byte[] u16(int v, boolean be) {
        return be ? new byte[] {(byte) (v >> 8), (byte) v} : new byte[] {(byte) v, (byte) (v >> 8)};
    }

    private static byte[] u32(int v, boolean be) {
        return be
            ? new byte[] {(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v}
            : new byte[] {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    /** Inserts a segment right after SOI and the JFIF APP0 segment (if present). */
    static byte[] spliceAfterHeader(byte[] jpeg, byte[] segment) {
        int offset = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            offset = 4 + (((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, offset);
        out.writeBytes(segment);
        out.write(jpeg, offset, jpeg.length - offset);
        return out.toByteArray();
    }

    public static byte[] jpegWithExif(int orientation, boolean bigEndian) throws IOException {
        byte[] jpeg = encode(markerImage(BufferedImage.TYPE_INT_RGB), "jpg");
        return spliceAfterHeader(jpeg, app1Exif(orientation, bigEndian));
    }

    static MultipartFile upload(byte[] bytes, String filename, String contentType) {
        return new MockMultipartFile("files", filename, contentType, bytes);
    }

    static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean containsAscii(byte[] haystack, String needle) {
        return contains(haystack, needle.getBytes(StandardCharsets.US_ASCII));
    }

    static boolean isRedish(int rgb) {
        return ((rgb >> 16) & 0xFF) > 180 && ((rgb >> 8) & 0xFF) < 90 && (rgb & 0xFF) < 90;
    }

    static boolean isBluish(int rgb) {
        return (rgb & 0xFF) > 180 && ((rgb >> 16) & 0xFF) < 90 && ((rgb >> 8) & 0xFF) < 90;
    }

    /** EXIF spec: destination pixel for source pixel (x, y) of a W x H image, per orientation. */
    static int[] expectedPosition(int orientation, int x, int y) {
        return switch (orientation) {
            case 1 -> new int[] {x, y};
            case 2 -> new int[] {W - 1 - x, y};
            case 3 -> new int[] {W - 1 - x, H - 1 - y};
            case 4 -> new int[] {x, H - 1 - y};
            case 5 -> new int[] {y, x};
            case 6 -> new int[] {H - 1 - y, x};
            case 7 -> new int[] {H - 1 - y, W - 1 - x};
            case 8 -> new int[] {y, W - 1 - x};
            default -> throw new IllegalArgumentException("orientation " + orientation);
        };
    }

    static BufferedImage decode(MultipartFile file) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(file.getBytes()));
    }

    // ---------- tests ----------

    @Test
    void jpegExifSegmentAndGpsBytesAreRemovedAndDimensionsSurvive() throws Exception {
        byte[] input = jpegWithExif(1, true);
        assertThat(containsAscii(input, "Exif")).as("precondition: fixture carries Exif").isTrue();
        assertThat(containsAscii(input, "GPSLatitude")).isTrue();

        MultipartFile result = sanitizer.sanitize(upload(input, "photo.jpg", "image/jpeg"));

        byte[] out = result.getBytes();
        assertThat(contains(out, new byte[] {(byte) 0xFF, (byte) 0xE1})).as("no APP1 marker").isFalse();
        assertThat(containsAscii(out, "Exif")).isFalse();
        assertThat(containsAscii(out, "GPS")).isFalse();
        BufferedImage decoded = decode(result);
        assertThat(decoded.getWidth()).isEqualTo(W);
        assertThat(decoded.getHeight()).isEqualTo(H);
        assertThat(result.getContentType()).isEqualTo("image/jpeg");
        assertThat(result.getOriginalFilename()).isEqualTo("photo.jpg");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void exifOrientationIsAppliedToPixelsBeforeMetadataIsDropped(int orientation) throws Exception {
        MultipartFile result = sanitizer.sanitize(upload(jpegWithExif(orientation, true), "p.jpg", "image/jpeg"));

        BufferedImage decoded = decode(result);
        boolean swapped = orientation >= 5;
        assertThat(decoded.getWidth()).isEqualTo(swapped ? H : W);
        assertThat(decoded.getHeight()).isEqualTo(swapped ? W : H);

        int[] red = expectedPosition(orientation, 8, 8);
        int[] blue = expectedPosition(orientation, 24, 8);
        assertThat(isRedish(decoded.getRGB(red[0], red[1]))).as("red marker at %d,%d", red[0], red[1]).isTrue();
        assertThat(isBluish(decoded.getRGB(blue[0], blue[1]))).as("blue marker at %d,%d", blue[0], blue[1]).isTrue();
        assertThat(containsAscii(result.getBytes(), "Exif")).isFalse();
    }

    @Test
    void littleEndianExifOrientationIsHonoured() throws Exception {
        MultipartFile result = sanitizer.sanitize(upload(jpegWithExif(6, false), "p.jpg", "image/jpeg"));

        BufferedImage decoded = decode(result);
        assertThat(decoded.getWidth()).isEqualTo(H);
        assertThat(decoded.getHeight()).isEqualTo(W);
        int[] red = expectedPosition(6, 8, 8);
        assertThat(isRedish(decoded.getRGB(red[0], red[1]))).isTrue();
    }

    @Test
    void jpegWithoutExifIsReencodedWithSameGeometry() throws Exception {
        byte[] input = encode(markerImage(BufferedImage.TYPE_INT_RGB), "jpg");

        MultipartFile result = sanitizer.sanitize(upload(input, "plain.jpeg", "image/jpeg"));

        BufferedImage decoded = decode(result);
        assertThat(decoded.getWidth()).isEqualTo(W);
        assertThat(decoded.getHeight()).isEqualTo(H);
        assertThat(isRedish(decoded.getRGB(8, 8))).isTrue();
    }

    @Test
    void pngStaysPngWithoutAncillaryChunksAndKeepsAlpha() throws Exception {
        BufferedImage img = markerImage(BufferedImage.TYPE_INT_ARGB);
        img.setRGB(40, 28, 0x00000000); // fully transparent pixel
        byte[] png = withPngTextChunk(encode(img, "png"), "Comment", "secret-location-note");
        assertThat(containsAscii(png, "secret-location-note")).isTrue();

        MultipartFile result = sanitizer.sanitize(upload(png, "shot.png", "image/png"));

        byte[] out = result.getBytes();
        assertThat(out[1]).isEqualTo((byte) 'P');
        assertThat(containsAscii(out, "tEXt")).isFalse();
        assertThat(containsAscii(out, "eXIf")).isFalse();
        assertThat(containsAscii(out, "secret-location-note")).isFalse();
        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getOriginalFilename()).isEqualTo("shot.png");
        BufferedImage decoded = decode(result);
        assertThat(decoded.getWidth()).isEqualTo(W);
        assertThat(decoded.getRGB(40, 28) >>> 24).isZero();
    }

    @Test
    void filenameExtensionFollowsTheDetectedFormat() throws Exception {
        byte[] png = encode(markerImage(BufferedImage.TYPE_INT_RGB), "png");

        MultipartFile result = sanitizer.sanitize(upload(png, "misnamed.jpg", "image/jpeg"));

        assertThat(result.getOriginalFilename()).isEqualTo("misnamed.png");
        assertThat(result.getContentType()).isEqualTo("image/png");
    }

    @Test
    void unsupportedOrCorruptPayloadsAreRejectedWithBadRequest() {
        byte[] gif = "GIF89a".getBytes(StandardCharsets.ISO_8859_1);
        byte[] text = "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII);
        byte[] corruptJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 4, 1, 2, 3, 4, 5, 6};

        assertThatThrownBy(() -> sanitizer.sanitize(upload(gif, "a.gif", "image/gif")))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(upload(text, "a.jpg", "image/jpeg")))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(upload(corruptJpeg, "a.jpg", "image/jpeg")))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void imagesAboveThePixelLimitAreRejectedBeforeDecoding() throws Exception {
        ImageSanitizer strict = new ImageSanitizer(W * H - 1);
        byte[] jpeg = encode(markerImage(BufferedImage.TYPE_INT_RGB), "jpg");

        assertThatThrownBy(() -> strict.sanitize(upload(jpeg, "big.jpg", "image/jpeg")))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("dimensiones");
        assertThat(new ImageSanitizer(W * H).sanitize(upload(jpeg, "ok.jpg", "image/jpeg")).getSize()).isPositive();
    }

    @Test
    void emptyFilesPassThroughUntouched() {
        MultipartFile empty = upload(new byte[0], "empty.jpg", "image/jpeg");
        assertThat(sanitizer.sanitize(empty)).isSameAs(empty);
    }

    // ---------- WebP (RIFF container, no pixel decoding) ----------

    private static final int ALL_META_FLAGS = FLAG_ICC | FLAG_EXIF | FLAG_XMP;

    @Test
    void webpExifXmpAndIccChunksAreDroppedAndVp8xFlagsAreCleared() throws Exception {
        byte[] input = riff(
            chunk("VP8X", vp8xPayload(ALL_META_FLAGS | FLAG_ALPHA, W, H)),
            chunk("ICCP", "ICCP-profile-bytes-odd"),
            chunk("ALPH", new byte[] {1, 2, 3}),
            chunk("VP8L", vp8lPayload(W, H)),
            chunk("EXIF", exifPayload()),
            chunk("XMP ", xmpPayload()));
        assertThat(containsAscii(input, "Exif")).as("precondition").isTrue();
        assertThat(containsAscii(input, "XMP ")).isTrue();
        assertThat(containsAscii(input, "ICCP")).isTrue();

        MultipartFile result = sanitizer.sanitize(upload(input, "photo.webp", "image/webp"));

        byte[] out = result.getBytes();
        assertThat(chunkTypes(out)).containsExactly("VP8X", "ALPH", "VP8L");
        assertThat(chunkPayload(out, "VP8X")[0] & 0xFF).isEqualTo(FLAG_ALPHA);
        assertThat(declaredRiffSize(out)).isEqualTo(out.length - 8L);
        assertThat(containsAscii(out, "Exif")).isFalse();
        assertThat(containsAscii(out, "XMP ")).isFalse();
        assertThat(containsAscii(out, "ICCP")).isFalse();
        assertThat(containsAscii(out, "GPS")).isFalse();
        assertThat(result.getContentType()).isEqualTo("image/webp");
        assertThat(result.getOriginalFilename()).isEqualTo("photo.webp");
    }

    @Test
    void plainLosslessWebpKeepsItsImageDataUntouched() throws Exception {
        byte[] input = WebpTestSupport.simpleLossless(W, H);

        byte[] out = sanitizer.sanitize(upload(input, "plain.webp", "image/webp")).getBytes();

        assertThat(out).isEqualTo(input);
    }

    @Test
    void animationChunksAndFlagAreKeptWhileMetadataIsDropped() throws Exception {
        byte[] input = riff(
            chunk("VP8X", vp8xPayload(FLAG_ANIMATION | FLAG_ALPHA | FLAG_EXIF, W, H)),
            chunk("ANIM", new byte[] {0, 0, 0, 0, 0, 0}),
            chunk("ANMF", new byte[16]),
            chunk("EXIF", exifPayload()));

        byte[] out = sanitizer.sanitize(upload(input, "anim.webp", "image/webp")).getBytes();

        assertThat(chunkTypes(out)).containsExactly("VP8X", "ANIM", "ANMF");
        assertThat(chunkPayload(out, "VP8X")[0] & 0xFF).isEqualTo(FLAG_ANIMATION | FLAG_ALPHA);
        assertThat(declaredRiffSize(out)).isEqualTo(out.length - 8L);
    }

    @Test
    void unknownTopLevelChunksAreDroppedAndOddSizedChunksAreWalkedWithPadding() throws Exception {
        byte[] input = riff(
            chunk("VP8X", vp8xPayload(0, W, H)),
            chunk("junk", "odd"),
            chunk("SECR", "hidden-location-note!"),
            chunk("VP8 ", vp8Payload(W, H)),
            chunk("XMP ", "x"));

        byte[] out = sanitizer.sanitize(upload(input, "a.webp", "image/webp")).getBytes();

        assertThat(chunkTypes(out)).containsExactly("VP8X", "VP8 ");
        assertThat(containsAscii(out, "hidden-location-note")).isFalse();
        assertThat(declaredRiffSize(out)).isEqualTo(out.length - 8L);
    }

    @Test
    void bytesAfterTheDeclaredRiffEndAreDropped() throws Exception {
        byte[] clean = WebpTestSupport.simpleLossless(W, H);
        byte[] trailer = "Exif-trailing-secret".getBytes(StandardCharsets.US_ASCII);
        byte[] input = java.util.Arrays.copyOf(clean, clean.length + trailer.length);
        System.arraycopy(trailer, 0, input, clean.length, trailer.length);

        byte[] out = sanitizer.sanitize(upload(input, "t.webp", "image/webp")).getBytes();

        assertThat(containsAscii(out, "Exif")).isFalse();
        assertThat(out).hasSize(clean.length);
    }

    @Test
    void webpIsDetectedByMagicBytesNotByClientContentTypeOrExtension() throws Exception {
        byte[] input = riff(chunk("VP8L", vp8lPayload(W, H)), chunk("EXIF", exifPayload()));

        MultipartFile result = sanitizer.sanitize(upload(input, "vacation.jpg", "image/jpeg"));

        assertThat(result.getContentType()).isEqualTo("image/webp");
        assertThat(result.getOriginalFilename()).isEqualTo("vacation.webp");
        assertThat(chunkTypes(result.getBytes())).containsExactly("VP8L");
    }

    @Test
    void malformedWebpContainersAreRejectedWithBadRequest() {
        byte[] valid = riff(chunk("VP8X", vp8xPayload(0, W, H)), chunk("VP8L", vp8lPayload(W, H)));
        byte[] truncated = java.util.Arrays.copyOf(valid, valid.length - 6);
        byte[] riffSizeBeyondInput = riffWithDeclaredSize(valid.length + 100L,
            chunk("VP8X", vp8xPayload(0, W, H)), chunk("VP8L", vp8lPayload(W, H)));
        byte[] riffSizeTooSmall = riffWithDeclaredSize(3,
            chunk("VP8X", vp8xPayload(0, W, H)), chunk("VP8L", vp8lPayload(W, H)));
        byte[] chunkBeyondEnd = riffWithChunkSize(1, 5000,
            chunk("VP8X", vp8xPayload(0, W, H)), chunk("VP8L", vp8lPayload(W, H)));
        byte[] hugeChunkSize = riffWithChunkSize(1, 0xFFFFFFF0L,
            chunk("VP8X", vp8xPayload(0, W, H)), chunk("VP8L", vp8lPayload(W, H)));
        byte[] noImageData = riff(chunk("VP8X", vp8xPayload(0, W, H)), chunk("EXIF", exifPayload()));
        byte[] shortVp8x = riff(chunk("VP8X", new byte[4]), chunk("VP8L", vp8lPayload(W, H)));
        byte[] badVp8lSignature = riff(chunk("VP8L", new byte[] {0x00, 0, 0, 0, 0, 1, 2}));
        byte[] justHeader = "RIFF\0\0\0\0WEBP".getBytes(StandardCharsets.ISO_8859_1);
        byte[] wave = riff(chunk("VP8L", vp8lPayload(W, H)));
        wave[8] = 'W';
        wave[9] = 'A';
        wave[10] = 'V';
        wave[11] = 'E';

        for (byte[] bad : new byte[][] {truncated, riffSizeBeyondInput, riffSizeTooSmall, chunkBeyondEnd,
            hugeChunkSize, noImageData, shortVp8x, badVp8lSignature, justHeader, wave}) {
            assertThatThrownBy(() -> sanitizer.sanitize(upload(bad, "bad.webp", "image/webp")))
                .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void webpDimensionsAboveThePixelLimitAreRejectedBeforeParsingFurther() {
        // Garbage after the first chunk would fail differently ("could not read") if parsing went on.
        byte[] vp8x = riff(chunk("VP8X", vp8xPayload(0, 20_000, 20_000)), chunk("VP8L", new byte[] {1}), chunk("EXIF", ""));
        byte[] vp8l = riff(chunk("VP8L", vp8lPayload(16_000, 16_000)), chunk("junk", "x"));
        byte[] vp8 = riff(chunk("VP8 ", vp8Payload(16_000, 16_000)), chunk("junk", "x"));

        for (byte[] huge : new byte[][] {vp8x, vp8l, vp8}) {
            assertThatThrownBy(() -> sanitizer.sanitize(upload(huge, "big.webp", "image/webp")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dimensiones");
        }
    }

    @Test
    void webpPixelLimitBoundaryIsInclusive() throws Exception {
        byte[] input = WebpTestSupport.simpleLossless(W, H);

        assertThat(new ImageSanitizer(W * H).sanitize(upload(input, "ok.webp", "image/webp")).getSize()).isPositive();
        assertThatThrownBy(() -> new ImageSanitizer(W * H - 1).sanitize(upload(input, "big.webp", "image/webp")))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("dimensiones");
    }

    @Test
    void webpInnerImageChunkAboveTheLimitIsRejectedEvenWhenCanvasIsSmall() {
        byte[] input = riff(chunk("VP8X", vp8xPayload(0, W, H)), chunk("VP8L", vp8lPayload(16_000, 16_000)));

        assertThatThrownBy(() -> sanitizer.sanitize(upload(input, "x.webp", "image/webp")))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("dimensiones");
    }

    // ---------- JPEG/PNG metadata coverage ----------

    @Test
    void pngAncillaryMetadataChunksAreAllRemoved() throws Exception {
        byte[] png = encode(markerImage(BufferedImage.TYPE_INT_RGB), "png");
        byte[] srgbProfile = java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB).getData();
        png = withPngChunk(png, "iTXt", "Comment\0\0\0\0\0secret-itxt".getBytes(StandardCharsets.ISO_8859_1));
        png = withPngChunk(png, "zTXt", concat("Comment\0\0".getBytes(StandardCharsets.ISO_8859_1), deflate("secret-ztxt")));
        png = withPngChunk(png, "eXIf", "MM\0*GPSLatitude=36.8201 secret-exif".getBytes(StandardCharsets.ISO_8859_1));
        png = withPngChunk(png, "iCCP", concat("sRGB\0\0".getBytes(StandardCharsets.ISO_8859_1), deflate(srgbProfile)));
        assertThat(pngChunkTypes(png)).contains("iTXt", "zTXt", "eXIf", "iCCP");

        byte[] out = sanitizer.sanitize(upload(png, "meta.png", "image/png")).getBytes();

        assertThat(pngChunkTypes(out)).isSubsetOf("IHDR", "PLTE", "tRNS", "IDAT", "IEND");
        assertThat(pngChunkTypes(out)).contains("IHDR", "IDAT", "IEND");
        assertThat(containsAscii(out, "secret")).isFalse();
        assertThat(containsAscii(out, "GPS")).isFalse();
    }

    @Test
    void jpegCommentAndApp13SegmentsAreRemoved() throws Exception {
        byte[] jpeg = encode(markerImage(BufferedImage.TYPE_INT_RGB), "jpg");
        jpeg = spliceAfterHeader(jpeg, segment(0xFE, "secret-comment".getBytes(StandardCharsets.US_ASCII)));
        jpeg = spliceAfterHeader(jpeg, segment(0xED, "Photoshop 3.0\0secret-iptc".getBytes(StandardCharsets.US_ASCII)));
        assertThat(jpegMarkersBeforeScan(jpeg)).contains(0xFE, 0xED);

        byte[] out = sanitizer.sanitize(upload(jpeg, "c.jpg", "image/jpeg")).getBytes();

        assertThat(jpegMarkersBeforeScan(out)).doesNotContain(0xFE, 0xED, 0xE1);
        assertThat(containsAscii(out, "secret")).isFalse();
        assertThat(containsAscii(out, "Photoshop")).isFalse();
    }

    @Test
    void secondApp1IsUsedForOrientationWhenTheFirstOneIsXmp() throws Exception {
        byte[] jpeg = encode(markerImage(BufferedImage.TYPE_INT_RGB), "jpg");
        jpeg = spliceAfterHeader(jpeg, app1Exif(6, true));
        byte[] xmp = ("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>GPSLatitude=36.8</x:xmpmeta>")
            .getBytes(StandardCharsets.ISO_8859_1);
        jpeg = spliceAfterHeader(jpeg, segment(0xE1, xmp)); // now XMP comes first, Exif second

        MultipartFile result = sanitizer.sanitize(upload(jpeg, "x.jpg", "image/jpeg"));

        BufferedImage decoded = decode(result);
        assertThat(decoded.getWidth()).isEqualTo(H);
        assertThat(decoded.getHeight()).isEqualTo(W);
        assertThat(containsAscii(result.getBytes(), "xmpmeta")).isFalse();
        assertThat(containsAscii(result.getBytes(), "Exif")).isFalse();
    }

    @Test
    void malformedExifBlocksNeverEscapeAsUnhandledExceptions() throws Exception {
        byte[] base = encode(markerImage(BufferedImage.TYPE_INT_RGB), "jpg");
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        byte[][] tiffs = {
            {'M', 'M', 0, 42, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xF0}, // IFD offset far beyond the block
            {'I', 'I', 42, 0, 8, 0, 0, 0, (byte) 0xFF, (byte) 0xFF},               // huge entry count, no entries
            {'M', 'M', 0, 42},                                                     // truncated TIFF header
            {},                                                                    // empty TIFF block
            {'X', 'Y', 0, 42, 0, 0, 0, 8, 0, 1}                                    // unknown byte order
        };
        for (byte[] tiff : tiffs) {
            byte[] jpeg = spliceAfterHeader(base, segment(0xE1, concat(exifHeader, tiff)));
            Throwable thrown = catchThrowable(() -> sanitizer.sanitize(upload(jpeg, "m.jpg", "image/jpeg")));
            assertThat(thrown).isNull(); // orientation defaults to 1 and the image is still cleaned
        }
        // A file cut in the middle of the Exif block is undecodable: rejected with the 400 policy.
        byte[] cut = java.util.Arrays.copyOf(jpegWithExif(6, true), 40);
        Throwable thrown = catchThrowable(() -> sanitizer.sanitize(upload(cut, "cut.jpg", "image/jpeg")));
        assertThat(thrown).isInstanceOf(BadRequestException.class);
    }

    @Test
    void inputAboveTheMaximumSizeIsRejectedBeforeAnyProcessing() {
        byte[] big = new byte[ImageSanitizer.MAX_INPUT_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;

        assertThatThrownBy(() -> sanitizer.sanitize(upload(big, "big.jpg", "image/jpeg")))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("tamano");
    }

    @Test
    void pixelLimitIsCheckedFromTheHeaderBeforeAnyPixelIsDecoded() throws Exception {
        // A real JPEG whose SOF header now declares 20000 x 20000. If decoding were attempted first the JDK
        // would fail with "could not read"; the limit message proves only the header was consulted.
        byte[] jpeg = encode(markerImage(BufferedImage.TYPE_INT_RGB), "jpg");
        int sof = indexOfSof0(jpeg);
        jpeg[sof + 5] = (byte) (20_000 >> 8);
        jpeg[sof + 6] = (byte) (20_000 & 0xFF);
        jpeg[sof + 7] = (byte) (20_000 >> 8);
        jpeg[sof + 8] = (byte) (20_000 & 0xFF);

        assertThatThrownBy(() -> sanitizer.sanitize(upload(jpeg, "huge.jpg", "image/jpeg")))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("dimensiones");
    }

    @Test
    void grayscaleJpegIsSanitizedToRgbJpeg() throws Exception {
        byte[] gray = encode(markerImage(BufferedImage.TYPE_BYTE_GRAY), "jpg");

        MultipartFile result = sanitizer.sanitize(upload(gray, "g.jpg", "image/jpeg"));

        BufferedImage decoded = decode(result);
        assertThat(decoded.getWidth()).isEqualTo(W);
        assertThat(decoded.getHeight()).isEqualTo(H);
        assertThat(result.getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void indexedPngIsSanitizedAndKeepsItsGeometry() throws Exception {
        byte[] indexed = encode(markerImage(BufferedImage.TYPE_BYTE_INDEXED), "png");

        MultipartFile result = sanitizer.sanitize(upload(indexed, "i.png", "image/png"));

        BufferedImage decoded = decode(result);
        assertThat(decoded.getWidth()).isEqualTo(W);
        assertThat(decoded.getHeight()).isEqualTo(H);
        assertThat(result.getContentType()).isEqualTo("image/png");
    }

    @Test
    void sixteenBitPngIsSanitizedToEightBitPng() throws Exception {
        byte[] sixteen = encode(markerImage(BufferedImage.TYPE_USHORT_GRAY), "png");

        MultipartFile result = sanitizer.sanitize(upload(sixteen, "s.png", "image/png"));

        BufferedImage decoded = decode(result);
        assertThat(decoded.getWidth()).isEqualTo(W);
        assertThat(decoded.getHeight()).isEqualTo(H);
    }

    // ---------- memory / disk guarantees ----------

    @Test
    void rawUploadIsNeverCachedOnDisk() throws Exception {
        // With ImageIO's disk cache enabled, createImageInputStream(InputStream) writes the raw upload to the
        // cache directory. Point it at a directory that no longer exists: a disk-cache stream would fail there,
        // while a memory-cache stream does not care.
        byte[] rawJpeg = jpegWithExif(1, true); // fixtures are encoded with ImageIO too: build them before touching the cache
        boolean previousUseCache = ImageIO.getUseCache();
        java.io.File previousDir = ImageIO.getCacheDirectory();
        java.nio.file.Path gone = java.nio.file.Files.createTempDirectory("imageio-cache-gone");
        try {
            ImageIO.setCacheDirectory(gone.toFile());
            java.nio.file.Files.delete(gone);
            ImageIO.setUseCache(true);

            MultipartFile result = sanitizer.sanitize(upload(rawJpeg, "p.jpg", "image/jpeg"));

            assertThat(result.getSize()).isPositive();
        } finally {
            ImageIO.setUseCache(previousUseCache);
            ImageIO.setCacheDirectory(previousDir);
        }
    }

    @Test
    void defaultPixelLimitIsTwelveMegapixels() {
        String expression = ImageSanitizer.class.getConstructors()[0].getParameters()[0]
            .getAnnotation(org.springframework.beans.factory.annotation.Value.class).value();

        assertThat(expression).isEqualTo("${app.upload.image.max-pixels:12000000}");
    }

    // ---------- test helpers ----------

    private static byte[] segment(int marker, byte[] payload) {
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(marker);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] deflate(String text) {
        return deflate(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static byte[] deflate(byte[] data) {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[data.length + 64];
        int n = deflater.deflate(buffer);
        deflater.end();
        return java.util.Arrays.copyOf(buffer, n);
    }

    /** Chunk types in file order (skips the 8-byte signature). */
    private static java.util.List<String> pngChunkTypes(byte[] png) {
        java.util.List<String> types = new java.util.ArrayList<>();
        int pos = 8;
        while (pos + 8 <= png.length) {
            long length = ((png[pos] & 0xFFL) << 24) | ((png[pos + 1] & 0xFFL) << 16)
                | ((png[pos + 2] & 0xFFL) << 8) | (png[pos + 3] & 0xFFL);
            types.add(new String(png, pos + 4, 4, StandardCharsets.ISO_8859_1));
            pos += 12 + (int) length;
        }
        return types;
    }

    /** Marker codes of the segments preceding the start-of-scan marker. */
    private static java.util.List<Integer> jpegMarkersBeforeScan(byte[] jpeg) {
        java.util.List<Integer> markers = new java.util.ArrayList<>();
        int pos = 2;
        while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xDA) {
                break;
            }
            markers.add(marker);
            pos += 2 + (((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF));
        }
        return markers;
    }

    private static int indexOfSof0(byte[] jpeg) {
        int pos = 2;
        while (pos + 4 <= jpeg.length) {
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xC0) {
                return pos;
            }
            pos += 2 + (((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF));
        }
        throw new IllegalStateException("no SOF0 marker in fixture");
    }

    private static byte[] withPngChunk(byte[] png, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        chunk.writeBytes(u32(data.length, true));
        chunk.writeBytes(typeBytes);
        chunk.writeBytes(data);
        chunk.writeBytes(u32((int) crc.getValue(), true));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, 33);
        out.writeBytes(chunk.toByteArray());
        out.write(png, 33, png.length - 33);
        return out.toByteArray();
    }

    /** Inserts a tEXt chunk right after IHDR (8-byte signature + 25-byte IHDR chunk). */
    private static byte[] withPngTextChunk(byte[] png, String key, String value) {
        byte[] data = (key + " " + value).getBytes(StandardCharsets.ISO_8859_1);
        byte[] type = "tEXt".getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data);
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        chunk.writeBytes(u32(data.length, true));
        chunk.writeBytes(type);
        chunk.writeBytes(data);
        chunk.writeBytes(u32((int) crc.getValue(), true));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, 33);
        out.writeBytes(chunk.toByteArray());
        out.write(png, 33, png.length - 33);
        return out.toByteArray();
    }
}
