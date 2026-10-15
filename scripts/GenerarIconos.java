import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Ejecutar desde la raiz: java scripts/GenerarIconos.java */
class GenerarIconos {
    public static void main(String[] args) throws Exception {
        Path source = Path.of("docs/marca/planificador-premium.png");
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null || original.getWidth() != original.getHeight()) {
            throw new IllegalArgumentException("El logo debe ser una imagen cuadrada valida: " + source);
        }
        Path directory = Path.of("src/main/resources/static/images/pwa");
        Files.createDirectories(directory);
        icon(original, directory.resolve("icon-192-v2.png"), 192, 1.0);
        icon(original, directory.resolve("icon-512-v2.png"), 512, 1.0);
        icon(original, directory.resolve("icon-maskable-512-v2.png"), 512, 0.84);
        icon(original, directory.resolve("apple-touch-icon-v2.png"), 180, 1.0);
        icon(original, directory.resolve("favicon-32-v2.png"), 32, 1.0);
    }

    private static void icon(BufferedImage original, Path path, int size, double scale) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.decode("#17191A"));
        g.fillRect(0, 0, size, size);
        // Margen adicional para conservar el monograma con las mascaras de Android.
        int contentSize = (int) Math.round(size * scale);
        int inset = (size - contentSize) / 2;
        g.drawImage(original, inset, inset, contentSize, contentSize, null);
        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }
}
