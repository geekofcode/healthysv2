package org.novasos.healthysv2.profilecontract;

import static org.assertj.core.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SignatureImageTests {
  static byte[] png(boolean signed) throws IOException {
    var image = new BufferedImage(400, 120, BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, 400, 120);
    if (signed) {
      graphics.setColor(Color.BLUE);
      graphics.setStroke(new BasicStroke(3));
      graphics.drawLine(30, 80, 100, 25);
      graphics.drawLine(100, 25, 170, 85);
      graphics.drawArc(130, 30, 170, 65, 0, 240);
    }
    graphics.dispose();
    var out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  static String data(boolean signed) throws IOException {
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(png(signed));
  }

  @Test
  void blankCanvasCannotBeAcceptedAsSignature() throws Exception {
    assertThatThrownBy(() -> SignatureImage.normalize(data(false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SIGNATURE_REQUIRED");
  }

  @Test
  void realStrokeIsDecodedAndReencodedAsPng() throws Exception {
    byte[] result = SignatureImage.normalize(data(true));
    assertThat(ImageIO.read(new ByteArrayInputStream(result)).getWidth()).isEqualTo(400);
  }

  @Test
  void arbitraryImageDataUrlsAreRejected() {
    assertThatThrownBy(() -> SignatureImage.normalize("data:image/svg+xml;base64,PHN2Zz4="))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
