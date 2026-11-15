package com.wx.community.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LabeledQrImageRenderer {
    static final int CANVAS_WIDTH = 520;
    static final int CANVAS_HEIGHT = 500;
    private static final int QR_SIZE = 420;
    private static final int QR_X = (CANVAS_WIDTH - QR_SIZE) / 2;
    private static final int QR_Y = 12;
    private static final int LABEL_MAX_WIDTH = 480;

    public byte[] render(String content, String label) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(matrix);

        BufferedImage image = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
            graphics.drawImage(qrImage, QR_X, QR_Y, null);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setColor(new Color(36, 55, 71));

            String normalizedLabel = normalizeLabel(label);
            Font font = labelFont(normalizedLabel, 22);
            graphics.setFont(font);
            FontMetrics metrics = graphics.getFontMetrics();
            while (font.getSize() > 16 && metrics.stringWidth(normalizedLabel) > LABEL_MAX_WIDTH) {
                font = font.deriveFont((float) font.getSize() - 1);
                graphics.setFont(font);
                metrics = graphics.getFontMetrics();
            }
            String fittedLabel = fitLabel(normalizedLabel, metrics, LABEL_MAX_WIDTH);
            int x = (CANVAS_WIDTH - metrics.stringWidth(fittedLabel)) / 2;
            int y = 468;
            graphics.drawString(fittedLabel, x, y);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }

    private Font labelFont(String label, int size) {
        Set<String> installedFonts = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            .stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        String[] candidates = {
            "Noto Sans CJK SC", "PingFang SC", "Microsoft YaHei", "WenQuanYi Micro Hei", "SimHei", "SansSerif"
        };
        for (String candidate : candidates) {
            if (!installedFonts.contains(candidate.toLowerCase(Locale.ROOT)) && !"SansSerif".equals(candidate)) continue;
            Font font = new Font(candidate, Font.PLAIN, size);
            if (font.canDisplayUpTo(label) == -1) return font;
        }
        return new Font("SansSerif", Font.PLAIN, size);
    }

    private String normalizeLabel(String label) {
        String value = label == null ? "活动" : label.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return value.isBlank() ? "活动" : value;
    }

    private String fitLabel(String label, FontMetrics metrics, int maxWidth) {
        if (metrics.stringWidth(label) <= maxWidth) return label;
        String suffix = "…";
        StringBuilder fitted = new StringBuilder();
        for (int offset = 0; offset < label.length();) {
            int codePoint = label.codePointAt(offset);
            String next = fitted + new String(Character.toChars(codePoint)) + suffix;
            if (metrics.stringWidth(next) > maxWidth) break;
            fitted.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        return fitted + suffix;
    }
}
