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
    private static final int MAX_OUTPUT_BYTES = 200_000;
    private static final float[] JPEG_QUALITIES = {0.82f, 0.62f, 0.42f, 0.25f};
    private static final int MIN_DIMENSION = 64;

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
                if (width <= 0 || height <= 0) throw unsupportedImage();

                // Decode very large images with source subsampling instead of rejecting them.
                // This keeps memory bounded while allowing the normal upload flow to compress them.
                int sample = 1;
                long pixels = (long) width * height;
                if (pixels > MAX_PIXELS) {
                    sample = (int) Math.ceil(Math.sqrt((double) pixels / MAX_PIXELS));
                }
                BufferedImage source;
                if (sample > 1) {
                    var params = reader.getDefaultReadParam();
                    params.setSourceSubsampling(sample, sample, 0, 0);
                    source = reader.read(0, params);
                } else {
                    source = reader.read(0);
                }
                if (source == null) throw unsupportedImage();
                boolean png = format.equals("png");
                BufferedImage resized = resize(source, source.getWidth(), source.getHeight(), format.equals("png"));
                try {
                    if (sample == 1 && resized == source && bytes.length < MAX_OUTPUT_BYTES && !png) {
                        return original(bytes, contentType);
                    }
                    if (png) {
                        byte[] encodedPng = encode(resized, "png", null);
                        if (encodedPng.length < MAX_OUTPUT_BYTES) {
                            return new ProcessedImage(encodedPng, "png", "image/png", true);
                        }
                        return compressedJpeg(resized);
                    }
                    return compressedJpeg(resized);
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
        return resizeTo(source, targetWidth, targetHeight, preserveAlpha);
    }

    private BufferedImage resizeTo(BufferedImage source, int targetWidth, int targetHeight, boolean preserveAlpha) {
        if (targetWidth == source.getWidth() && targetHeight == source.getHeight()) return source;

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
        return encode(image, format, null);
    }

    private byte[] encode(BufferedImage image, String format, Float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) throw new BusinessException("IMAGE_COMPRESSION_FAILED", "图片压缩失败，请更换图片");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (format.equals("jpeg") && params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality == null ? JPEG_QUALITIES[0] : quality);
            }
            writer.write(null, new IIOImage(image, null, null), params);
            output.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private ProcessedImage compressedJpeg(BufferedImage image) throws IOException {
        BufferedImage current = toOpaque(image);
        try {
            for (int attempt = 0; attempt < 8; attempt++) {
                byte[] smallest = null;
                for (float quality : JPEG_QUALITIES) {
                    byte[] encoded = encode(current, "jpeg", quality);
                    if (encoded.length < MAX_OUTPUT_BYTES) {
                        return new ProcessedImage(encoded, "jpg", "image/jpeg", true);
                    }
                    if (smallest == null || encoded.length < smallest.length) smallest = encoded;
                }
                int width = current.getWidth();
                int height = current.getHeight();
                if (Math.max(width, height) <= MIN_DIMENSION) {
                    throw new BusinessException("IMAGE_COMPRESSION_FAILED", "图片压缩后仍超过200KB，请更换图片");
                }
                double scale = Math.min(0.85, Math.max(0.50, Math.sqrt((double) MAX_OUTPUT_BYTES / smallest.length) * 0.90));
                int nextWidth = Math.max(1, (int) Math.round(width * scale));
                int nextHeight = Math.max(1, (int) Math.round(height * scale));
                if (nextWidth == width && nextHeight == height) {
                    throw new BusinessException("IMAGE_COMPRESSION_FAILED", "图片压缩后仍超过200KB，请更换图片");
                }
                BufferedImage next = resizeTo(current, nextWidth, nextHeight, false);
                if (next != current) current.flush();
                current = next;
            }
            throw new BusinessException("IMAGE_COMPRESSION_FAILED", "图片压缩后仍超过200KB，请更换图片");
        } finally {
            current.flush();
        }
    }

    private BufferedImage toOpaque(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) return source;
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private ProcessedImage original(byte[] bytes, String contentType) {
        return new ProcessedImage(bytes, "bin", contentType == null ? "application/octet-stream" : contentType, false);
    }

    private BusinessException unsupportedImage() {
        return new BusinessException("UNSUPPORTED_IMAGE_FORMAT", "目前仅支持 JPG、JPEG、PNG 图片");
    }

    public record ProcessedImage(byte[] bytes, String extension, String contentType, boolean compressed) {}
}
