package com.wx.community.service;

import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCompressionServiceTest {
    private final ImageCompressionService service = new ImageCompressionService();

    @Test
    void jpegWidthIsResizedToMaximumAndRemainsReadable() throws Exception {
        BufferedImage source = new BufferedImage(3000, 1500, BufferedImage.TYPE_INT_RGB);
        fill(source, Color.BLUE);
        byte[] original = encode(source, "jpeg");

        ImageCompressionService.ProcessedImage processed = service.process(original, "image/jpeg");
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(processed.bytes()));

        assertTrue(processed.compressed());
        assertEquals("jpg", processed.extension());
        assertEquals("image/jpeg", processed.contentType());
        assertNotNull(result);
        assertEquals(1920, result.getWidth());
        assertEquals(960, result.getHeight());
    }

    @Test
    void tallInfographicBelowMaximumWidthKeepsItsOriginalDimensions() throws Exception {
        BufferedImage source = new BufferedImage(1244, 10000, BufferedImage.TYPE_INT_RGB);
        fill(source, Color.WHITE);
        byte[] original = encode(source, "jpeg");

        ImageCompressionService.ProcessedImage processed = service.process(original, "image/jpeg");
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(processed.bytes()));

        assertFalse(processed.compressed());
        assertArrayEquals(original, processed.bytes());
        assertNotNull(result);
        assertEquals(1244, result.getWidth());
        assertEquals(10000, result.getHeight());
    }

    @Test
    void tallImageIsNotScaledDownByItsHeight() throws Exception {
        BufferedImage source = new BufferedImage(2400, 3200, BufferedImage.TYPE_INT_RGB);
        fill(source, Color.WHITE);
        byte[] original = encode(source, "jpeg");

        ImageCompressionService.ProcessedImage processed = service.process(original, "image/jpeg");
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(processed.bytes()));

        assertTrue(processed.compressed());
        assertNotNull(result);
        assertEquals(1920, result.getWidth());
        assertEquals(2560, result.getHeight());
    }

    @Test
    void pngKeepsTransparency() throws Exception {
        BufferedImage source = new BufferedImage(80, 40, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, new Color(255, 0, 0, 0).getRGB());
        source.setRGB(1, 0, new Color(0, 255, 0, 255).getRGB());
        byte[] original = encode(source, "png");

        ImageCompressionService.ProcessedImage processed = service.process(original, "image/png");
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(processed.bytes()));

        assertTrue(processed.compressed());
        assertEquals("png", processed.extension());
        assertEquals("image/png", processed.contentType());
        assertNotNull(result);
        assertEquals(80, result.getWidth());
        assertEquals(40, result.getHeight());
        assertEquals(0, (result.getRGB(0, 0) >>> 24) & 0xff);
        assertEquals(255, (result.getRGB(1, 0) >>> 24) & 0xff);
    }

    @Test
    void nonImageFileIsReturnedUnchanged() throws Exception {
        byte[] original = "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        ImageCompressionService.ProcessedImage processed = service.process(original, "text/plain");

        assertFalse(processed.compressed());
        assertEquals("bin", processed.extension());
        assertEquals("text/plain", processed.contentType());
        assertArrayEquals(original, processed.bytes());
    }

    @Test
    void unsupportedImageFormatIsRejected() throws Exception {
        BufferedImage source = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        byte[] gif = encode(source, "gif");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.process(gif, "image/gif"));

        assertEquals("UNSUPPORTED_IMAGE_FORMAT", exception.code);
    }

    @Test
    void emptyFileIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.process(new byte[0], "image/jpeg"));

        assertEquals("EMPTY_FILE", exception.code);
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output), "JRE has no " + format + " ImageIO writer");
        return output.toByteArray();
    }

    private static void fill(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
    }
}
