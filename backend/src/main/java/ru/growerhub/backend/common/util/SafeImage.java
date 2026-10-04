package ru.growerhub.backend.common.util;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import ru.growerhub.backend.common.contract.DomainException;

public final class SafeImage {
    private SafeImage() {}

    public static byte[] jpeg(byte[] source, int maxBytes, long maxPixels, int edge) {
        if (source == null || source.length == 0 || source.length > maxBytes) {
            throw new DomainException("unprocessable", "Фотография слишком большая или файл пуст");
        }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException();
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) throw new IllegalArgumentException();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
                    throw new DomainException("unprocessable", "У фотографии слишком высокое разрешение");
                }
                var original = reader.read(0);
                double scale = Math.min(1d, (double) edge / Math.max(width, height));
                var result = new BufferedImage(Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = result.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(original, 0, 0, result.getWidth(), result.getHeight(), null);
                } finally { graphics.dispose(); }
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(result, "jpeg", output)) throw new IllegalArgumentException();
                return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (DomainException ex) { throw ex; }
        catch (Exception ex) {
            throw new DomainException("unprocessable", "Не удалось прочитать фото. Выберите JPEG или PNG");
        }
    }
}
