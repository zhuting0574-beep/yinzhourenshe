package com.wx.community.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabeledQrImageRendererTest {
    private final LabeledQrImageRenderer renderer = new LabeledQrImageRenderer();

    @Test
    void rendersScannableQrCodeWithCaptionInsidePng() throws Exception {
        String content = "https://www.yinlinpai.com/test-api/api/mini/activities/42/open";
        byte[] png = renderer.render(content, "测试活动 · 活动码");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(image);
        assertEquals(520, image.getWidth());
        assertEquals(500, image.getHeight());

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        assertEquals(content, new MultiFormatReader().decode(bitmap).getText());

        boolean hasCaptionPixels = false;
        for (int y = 440; y < image.getHeight() && !hasCaptionPixels; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    hasCaptionPixels = true;
                    break;
                }
            }
        }
        assertTrue(hasCaptionPixels, "caption area should contain rendered text");
    }
}
