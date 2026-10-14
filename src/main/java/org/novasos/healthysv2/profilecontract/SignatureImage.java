package org.novasos.healthysv2.profilecontract;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;

final class SignatureImage {
  private SignatureImage() {}

  static byte[] normalize(String dataUrl) {
    if (dataUrl == null
        || !dataUrl.startsWith("data:image/png;base64,")
        || dataUrl.length() > 1500000)
      throw new IllegalArgumentException("INVALID_SIGNATURE_IMAGE");
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(dataUrl.substring(22));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("INVALID_SIGNATURE_IMAGE");
    }
    if (bytes.length < 32 || bytes.length > 1000000)
      throw new IllegalArgumentException("INVALID_SIGNATURE_IMAGE");
    byte[] magic = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    for (int i = 0; i < magic.length; i++)
      if (bytes[i] != magic[i]) throw new IllegalArgumentException("INVALID_SIGNATURE_IMAGE");
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IllegalArgumentException("INVALID_SIGNATURE_IMAGE");
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 40
            || height < 20
            || width > 2048
            || height > 1024
            || (long) width * height > 1500000)
          throw new IllegalArgumentException("INVALID_SIGNATURE_DIMENSIONS");
        BufferedImage image = reader.read(0);
        int ink = 0, minX = width, maxX = 0, minY = height, maxY = 0;
        for (int y = 0; y < height; y++)
          for (int x = 0; x < width; x++) {
            int pixel = image.getRGB(x, y);
            if (((pixel >>> 24) & 255) > 32
                && (((pixel >> 16) & 255) < 220
                    || ((pixel >> 8) & 255) < 220
                    || (pixel & 255) < 220)) {
              ink++;
              minX = Math.min(minX, x);
              maxX = Math.max(maxX, x);
              minY = Math.min(minY, y);
              maxY = Math.max(maxY, y);
            }
          }
        if (ink < 50 || maxX - minX < 12 || maxY - minY < 4)
          throw new IllegalArgumentException("SIGNATURE_REQUIRED");
        var out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out))
          throw new IllegalArgumentException("INVALID_SIGNATURE_IMAGE");
        return out.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("INVALID_SIGNATURE_IMAGE");
    }
  }
}
