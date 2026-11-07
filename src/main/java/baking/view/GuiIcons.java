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

/** Small vector icons painted with Java2D. */
public final class GuiIcons {
    private static final Color HEART = new Color(0xE5, 0x39, 0x35);
    private static final Color ROSE = BakingTheme.TEXT;

    private GuiIcons() { }

    public static Icon heart(int size) { return new HeartIcon(size); }
    public static Icon measuringCup(int size) { return new MeasuringCupIcon(size); }

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
}
