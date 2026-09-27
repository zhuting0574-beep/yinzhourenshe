package com.wx.community.service;

import com.wx.community.api.BusinessException;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Service
public class ImageCompressionService {
    private static final int MAX_WIDTH = 1920;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.90f;

    public ProcessedImage process(byte[] bytes, String contentType) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("EMPTY_FILE", "请选择文件");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return original(bytes, contentType);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
                    throw unsupportedImage();
                }
                return original(bytes, contentType);
            }

            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase();
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png")) {
                    throw unsupportedImage();
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new BusinessException("IMAGE_TOO_LARGE", "图片分辨率过大，请先缩小图片");
                }

                BufferedImage source = reader.read(0);
                BufferedImage resized = resize(source, width, height, format.equals("png"));
                try {
                    if (resized == source && !format.equals("png")) {
                        return original(bytes, contentType);
                    }
                    byte[] encoded = encode(resized, format.equals("png") ? "png" : "jpeg");
                    String extension = format.equals("png") ? "png" : "jpg";
                    String mimeType = format.equals("png") ? "image/png" : "image/jpeg";
                    return new ProcessedImage(encoded, extension, mimeType, true);
                } finally {
                    if (resized != source) resized.flush();
                    source.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage resize(BufferedImage source, int width, int height, boolean preserveAlpha) {
        double ratio = Math.min(1d, (double) MAX_WIDTH / width);
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        if (targetWidth == width && targetHeight == height) return source;

        int type = preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, type);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!preserveAlpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encode(BufferedImage image, String format) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new BusinessException("IMAGE_COMPRESSION_FAILED", "图片压缩失败，请更换图片");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (format.equals("jpeg") && params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), params);
            output.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private ProcessedImage original(byte[] bytes, String contentType) {
        return new ProcessedImage(bytes, "bin", contentType == null ? "application/octet-stream" : contentType, false);
    }

    private BusinessException unsupportedImage() {
        return new BusinessException("UNSUPPORTED_IMAGE_FORMAT", "目前仅支持 JPG、JPEG、PNG 图片");
    }

    public record ProcessedImage(byte[] bytes, String extension, String contentType, boolean compressed) {}
}
