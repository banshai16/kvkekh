import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;

public class Sheet {
    public static void main(String[] a) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(a[0]));
        String base = a[1];
        Path out = Path.of(a[2]);
        int cols = 4, rows = 4, cw = 320, ch = 260, per = cols * rows, sheet = 0;
        for (int i = 0; i < lines.size(); i += per) {
            BufferedImage img = new BufferedImage(cols * cw, rows * ch, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            for (int j = 0; j < per && i + j < lines.size(); j++) {
                String[] p = lines.get(i + j).split("[|]");
                int x = (j % cols) * cw, y = (j / cols) * ch;
                StringBuilder enc = new StringBuilder();
                for (String seg : p[1].split("/")) {
                    if (enc.length() > 0) enc.append('/');
                    enc.append(URLEncoder.encode(seg, StandardCharsets.UTF_8).replace("+", "%20"));
                }
                try {
                    BufferedImage t = ImageIO.read(URI.create(base + enc).toURL());
                    double s = Math.min((cw - 8.0) / t.getWidth(), (ch - 30.0) / t.getHeight());
                    g.drawImage(t, x + 4, y + 4, (int) (t.getWidth() * s), (int) (t.getHeight() * s), null);
                } catch (Exception e) {
                    g.setColor(Color.RED);
                    g.drawString("ERR " + e.getMessage(), x + 10, y + 50);
                }
                g.setColor(Color.BLACK);
                g.drawString((i + j) + " " + p[0] + " #" + p[2], x + 4, y + ch - 10);
            }
            g.dispose();
            ImageIO.write(img, "jpg", out.resolve(String.format("sheet%02d.jpg", sheet++)).toFile());
        }
        System.out.println("sheets: " + sheet);
    }
}
