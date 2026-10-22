package in.nic.kvkekh.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class ImageDerivativesTest {

    /** A 3x2 image with a marker pixel at the top-left corner. */
    private static BufferedImage marked() {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);
        return image;
    }

    @Test
    void rotate90ClockwiseSwapsDimensionsAndMovesTopLeftToTopRight() {
        BufferedImage out = ImageDerivatives.orient(marked(), 6);
        assertThat(out.getWidth()).isEqualTo(2);
        assertThat(out.getHeight()).isEqualTo(3);
        assertThat(out.getRGB(1, 0) & 0xFFFFFF).isEqualTo(0xFF0000);
    }

    @Test
    void rotate90AntiClockwiseMovesTopLeftToBottomLeft() {
        BufferedImage out = ImageDerivatives.orient(marked(), 8);
        assertThat(out.getRGB(0, 2) & 0xFFFFFF).isEqualTo(0xFF0000);
    }

    @Test
    void rotate180MovesTopLeftToBottomRight() {
        BufferedImage out = ImageDerivatives.orient(marked(), 3);
        assertThat(out.getRGB(2, 1) & 0xFFFFFF).isEqualTo(0xFF0000);
    }

    @Test
    void normalOrientationIsUnchanged() {
        BufferedImage in = marked();
        assertThat(ImageDerivatives.orient(in, 1)).isSameAs(in);
    }
}
