package baking.view;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Ellipse2D;

/** Small vector icons painted with Java2D. */
public final class GuiIcons {
    private static final Color HEART = new Color(0xE5, 0x39, 0x35);
    private static final Color ROSE = BakingTheme.TEXT;
    private static final Color PINK = BakingTheme.SOFT_PINK;

    private GuiIcons() { }

    public static Icon heart(int size) { return new HeartIcon(size); }
    public static Icon measuringCup(int size) { return new MeasuringCupIcon(size); }
    public static Icon magnifier(int size) { return new MagnifierIcon(size); }
    public static Icon rollingPin(int size) { return new RollingPinIcon(size); }
    public static Icon jar(int size) { return new JarIcon(size); }
    public static Icon sparkle(int size) { return new SparkleIcon(size); }

    private static Graphics2D graphics(Graphics graphics, int x, int y, int size, int baseSize) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size / (double) baseSize, size / (double) baseSize);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        return g;
    }

    private record HeartIcon(int size) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double scale = size / 24.0;
            g.scale(scale, scale);
            Path2D heart = new Path2D.Double();
            heart.moveTo(12, 21);
            heart.curveTo(10, 19, 2, 12.8, 2, 7.5);
            heart.curveTo(2, 2, 9, 1, 12, 6);
            heart.curveTo(15, 1, 22, 2, 22, 7.5);
            heart.curveTo(22, 12.8, 14, 19, 12, 21);
            heart.closePath();
            g.setColor(HEART);
            g.fill(heart);
            g.dispose();
        }
    }

    private record MeasuringCupIcon(int size) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double scale = size / 28.0;
            g.scale(scale, scale);
            g.setColor(ROSE);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D cup = new Path2D.Double();
            cup.moveTo(4, 8);
            cup.lineTo(24, 8);
            cup.lineTo(21, 24);
            cup.quadTo(20.5, 26, 18.5, 26);
            cup.lineTo(9.5, 26);
            cup.quadTo(7.5, 26, 7, 24);
            cup.closePath();
            g.draw(cup);
            g.draw(new RoundRectangle2D.Double(1, 10, 7, 8, 4, 4));
            for (int i = 0; i < 3; i++) {
                double tickY = 12 + i * 4;
                g.drawLine(18, (int) tickY, 22, (int) tickY);
            }
            g.drawLine(8, 19, 20, 19);
            g.dispose();
        }
    }

    private record MagnifierIcon(int size) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = graphics(graphics, x, y, size, 24);
            g.setColor(PINK);
            g.fill(new Ellipse2D.Double(2, 2, 14, 14));
            g.setColor(ROSE);
            g.draw(new Ellipse2D.Double(2, 2, 14, 14));
            g.drawLine(14, 14, 22, 22);
            g.dispose();
        }
    }

    private record RollingPinIcon(int size) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = graphics(graphics, x, y, size, 24);
            g.rotate(-Math.PI / 5, 12, 12);
            g.setColor(PINK);
            g.fillRoundRect(5, 5, 14, 14, 5, 5);
            g.setColor(ROSE);
            g.drawRoundRect(5, 5, 14, 14, 5, 5);
            g.drawLine(2, 12, 5, 12);
            g.drawLine(19, 12, 22, 12);
            g.dispose();
        }
    }

    private record JarIcon(int size) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = graphics(graphics, x, y, size, 24);
            g.setColor(PINK);
            g.fillRoundRect(5, 7, 14, 15, 4, 4);
            g.setColor(ROSE);
            g.drawRoundRect(5, 7, 14, 15, 4, 4);
            g.drawRoundRect(4, 3, 16, 5, 2, 2);
            g.drawLine(8, 12, 16, 12);
            g.dispose();
        }
    }

    private record SparkleIcon(int size) implements Icon {
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = graphics(graphics, x, y, size, 24);
            Path2D sparkle = new Path2D.Double();
            sparkle.moveTo(12, 1);
            sparkle.lineTo(15, 9);
            sparkle.lineTo(23, 12);
            sparkle.lineTo(15, 15);
            sparkle.lineTo(12, 23);
            sparkle.lineTo(9, 15);
            sparkle.lineTo(1, 12);
            sparkle.lineTo(9, 9);
            sparkle.closePath();
            g.setColor(PINK);
            g.fill(sparkle);
            g.setColor(ROSE);
            g.draw(sparkle);
            g.dispose();
        }
    }
}
