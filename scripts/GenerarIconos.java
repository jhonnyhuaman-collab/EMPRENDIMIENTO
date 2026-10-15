import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Ejecutar desde la raiz: java scripts/GenerarIconos.java */
class GenerarIconos {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of("src/main/resources/static/images/pwa");
        Files.createDirectories(directory);
        icon(directory.resolve("icon-192.png"), 192);
        icon(directory.resolve("icon-512.png"), 512);
        icon(directory.resolve("icon-maskable-512.png"), 512);
        icon(directory.resolve("apple-touch-icon.png"), 180);
    }

    private static void icon(Path path, int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / 512.0, size / 512.0);
        g.setColor(Color.decode("#235B4E"));
        g.fillRect(0, 0, 512, 512);

        // El birrete queda dentro del circulo seguro de los iconos adaptativos.
        Path2D base = new Path2D.Double();
        base.moveTo(152, 250);
        base.lineTo(360, 250);
        base.lineTo(360, 310);
        base.quadTo(256, 378, 152, 310);
        base.closePath();
        g.setColor(Color.decode("#9FC6D4"));
        g.fill(base);
        Path2D cap = new Path2D.Double();
        cap.moveTo(90, 215);
        cap.lineTo(256, 150);
        cap.lineTo(422, 215);
        cap.lineTo(256, 282);
        cap.closePath();
        g.setColor(Color.decode("#F8FBFA"));
        g.fill(cap);
        g.setColor(Color.decode("#EACAA6"));
        g.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(256, 215, 393, 245);
        g.drawLine(393, 245, 393, 311);
        g.fillRoundRect(383, 303, 20, 34, 10, 10);
        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }
}
