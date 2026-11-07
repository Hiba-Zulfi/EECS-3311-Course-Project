package baking.view;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.Locale;

/** Central styling for the Baking Agent Swing interface. */
public final class BakingTheme {
    public static final Color BACKGROUND = Color.decode("#FFE4EC");
    public static final Color SOFT_PINK = Color.decode("#F8BBD0");
    public static final Color DEEP_PINK = Color.decode("#F48FB1");
    public static final Color TEXT = Color.decode("#5A2A3A");
    public static final Color SURFACE = new Color(255, 250, 252);

    private BakingTheme() { }

    public static void apply(JFrame frame, Container root, JLabel banner, JTabbedPane tabs) {
        Color rose = TEXT;
        Font body = bodyFont(14);
        Font heading = body.deriveFont(Font.BOLD, 16f);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", rose);
        UIManager.put("Label.font", body);
        UIManager.put("Button.background", SOFT_PINK);
        UIManager.put("Button.foreground", rose);
        UIManager.put("Button.font", body);
        UIManager.put("TextField.background", SURFACE);
        UIManager.put("TextArea.background", SURFACE);
        UIManager.put("List.background", SURFACE);
        UIManager.put("TabbedPane.background", BACKGROUND);
        UIManager.put("TabbedPane.selected", DEEP_PINK);
        UIManager.put("TabbedPane.contentAreaColor", DEEP_PINK);
        SwingUtilities.updateComponentTreeUI(frame);

        frame.getContentPane().setBackground(BACKGROUND);
        applyTo(root, body, heading);
        banner.setOpaque(true);
        banner.setBackground(SOFT_PINK);
        banner.setForeground(TEXT);
        banner.setFont(heading);
        banner.setBorder(BorderFactory.createCompoundBorder(new LineBorder(DEEP_PINK, 2, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        tabs.setFont(body.deriveFont(Font.BOLD, 14f));
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override protected void paintTabBackground(Graphics graphics, int placement, int index,
                    int x, int y, int width, int height, boolean selected) {
                graphics.setColor(selected ? DEEP_PINK : BACKGROUND);
                graphics.fillRect(x, y, width, height);
            }

            @Override protected void paintTabBorder(Graphics graphics, int placement, int index,
                    int x, int y, int width, int height, boolean selected) {
                graphics.setColor(DEEP_PINK);
                graphics.drawRect(x, y, width, height);
            }

            @Override protected void paintContentBorder(Graphics graphics, int placement, int selectedIndex) {
                graphics.setColor(DEEP_PINK);
                int x = tabPane.getInsets().left;
                int y = tabPane.getInsets().top;
                int width = tabPane.getWidth() - tabPane.getInsets().left - tabPane.getInsets().right;
                int height = tabPane.getHeight() - tabPane.getInsets().top - tabPane.getInsets().bottom;
                graphics.drawRect(x, y, width - 1, height - 1);
            }
        });
        for (int i = 0; i < tabs.getTabCount(); i++) {
            tabs.setBackgroundAt(i, BACKGROUND);
            tabs.setForegroundAt(i, TEXT);
        }
    }

    private static void applyTo(Component component, Font body, Font heading) {
        component.setFont(body);
        component.setForeground(TEXT);
        if (component instanceof JPanel panel) panel.setBackground(BACKGROUND);
        if (component instanceof JTextComponent text) {
            text.setBackground(SURFACE);
            text.setForeground(TEXT);
            text.setCaretColor(TEXT);
        }
        if (component instanceof JList<?> list) {
            list.setBackground(SURFACE);
            list.setForeground(TEXT);
        }
        if (component instanceof JTabbedPane pane) pane.setBackground(BACKGROUND);
        if (component instanceof JLabel label && label.getName() != null
                && label.getName().toLowerCase(Locale.ROOT).contains("heading")) label.setFont(heading);
        if (component instanceof JButton button) styleButton(button);
        if (component instanceof Container container) {
            if (component instanceof JComponent swingComponent) styleBorder(swingComponent.getBorder(), heading);
            for (Component child : container.getComponents()) applyTo(child, body, heading);
        }
    }

    private static void styleButton(JButton button) {
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(SOFT_PINK);
        button.setForeground(TEXT);
        button.setFocusPainted(false);
        button.setBorder(new LineBorder(DEEP_PINK, 1, true));
        button.setMargin(new java.awt.Insets(7, 12, 7, 12));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent event) {
                if (button.isEnabled()) button.setBackground(DEEP_PINK);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent event) {
                button.setBackground(SOFT_PINK);
            }
            @Override public void mousePressed(java.awt.event.MouseEvent event) {
                if (button.isEnabled()) button.setBackground(DEEP_PINK.darker());
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent event) {
                if (button.isEnabled()) button.setBackground(button.getMousePosition() == null ? SOFT_PINK : DEEP_PINK);
            }
        });
    }

    private static void styleBorder(Border border, Font heading) {
        if (border instanceof TitledBorder titled) {
            titled.setTitleColor(TEXT);
            titled.setTitleFont(heading);
            titled.setBorder(new LineBorder(DEEP_PINK, 1, true));
        } else if (border instanceof CompoundBorder compound) {
            styleBorder(compound.getOutsideBorder(), heading);
            styleBorder(compound.getInsideBorder(), heading);
        }
    }

    private static String availableFont() {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT);
        if (Arrays.stream(families).anyMatch(name -> name.equalsIgnoreCase("Verdana"))) return "Verdana";
        if (Arrays.stream(families).anyMatch(name -> name.equalsIgnoreCase("Trebuchet MS"))) return "Trebuchet MS";
        return Font.SANS_SERIF;
    }

    public static Font bodyFont(int size) {
        return new Font(availableFont(), Font.PLAIN, size);
    }
}
