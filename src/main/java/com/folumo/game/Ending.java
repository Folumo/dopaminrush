package com.folumo.game;

import com.folumo.tree.Fmt;
import com.folumo.tree.Theme;
import com.folumo.tree.TreeCanvas;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.folumo.tree.I18n.t;

/**
 * The ending, played after buying the final node: zoom out over the whole tree, play the video
 * inside the window, roll the credits, then let the player continue or be reborn.
 */
final class Ending {
    private final JFrame frame;
    private final TreeCanvas canvas;
    private final Path video, credits;
    private final double nextMultiplier;
    private final Runnable onContinue, onReborn;
    private final JPanel overlay = new JPanel(new BorderLayout());

    /**
     * @param video   the ending video (mp4); skipped if missing or unplayable
     * @param credits text file, one line per credit line; lines starting with "# " are headings
     */
    Ending(JFrame frame, TreeCanvas canvas, Path video, Path credits, double nextMultiplier, Runnable onContinue, Runnable onReborn) {
        this.frame = frame;
        this.canvas = canvas;
        this.video = video;
        this.credits = credits;
        this.nextMultiplier = nextMultiplier;
        this.onContinue = onContinue;
        this.onReborn = onReborn;
    }

    void start() {
        overlay.setOpaque(false);
        overlay.addMouseListener(new MouseAdapter() {}); // block the game underneath while the ending runs
        frame.setGlassPane(overlay);
        overlay.setVisible(true);
        canvas.selected = null;
        canvas.zoomToFit(() -> {
            Timer pause = new Timer(2000, e -> playVideo()); // let the player take in the whole journey
            pause.setRepeats(false);
            pause.start();
        });
    }

    private void playVideo() {
        if (!Files.exists(video)) {
            System.err.println("No ending video at " + video.toAbsolutePath() + ", skipping to the credits");
            rollCredits();
            return;
        }
        JFXPanel fx = new JFXPanel(); // starts JavaFX inside Swing
        overlay.setOpaque(true);
        overlay.setBackground(Color.BLACK);
        overlay.add(fx, BorderLayout.CENTER);
        overlay.revalidate();

        AtomicBoolean finished = new AtomicBoolean();
        Runnable done = () -> {
            if (finished.compareAndSet(false, true)) SwingUtilities.invokeLater(() -> {
                overlay.remove(fx);
                rollCredits();
            });
        };
        Platform.setImplicitExit(false); // keep JavaFX alive after the video closes
        Platform.runLater(() -> {
            try {
                MediaPlayer player = new MediaPlayer(new Media(video.toUri().toString()));
                MediaView view = new MediaView(player);
                view.setPreserveRatio(true);
                StackPane root = new StackPane(view);
                root.setStyle("-fx-background-color: black;");
                view.fitWidthProperty().bind(root.widthProperty());
                view.fitHeightProperty().bind(root.heightProperty());
                fx.setScene(new Scene(root, javafx.scene.paint.Color.BLACK));
                player.setOnEndOfMedia(() -> {
                    player.dispose();
                    done.run();
                });
                player.setOnError(() -> {
                    System.err.println("Ending video failed: " + player.getError());
                    player.dispose();
                    done.run();
                });
                player.play();
            } catch (Exception e) {
                System.err.println("Ending video failed: " + e.getMessage());
                done.run();
            }
        });
    }

    private void rollCredits() {
        List<String> fallback = List.of("# Dopamin Rush", "", t("ending.thanks"));
        List<String> lines;
        try {
            lines = Files.exists(credits) ? Files.readAllLines(credits, StandardCharsets.UTF_8) : fallback;
        } catch (Exception e) {
            System.err.println("Couldn't read credits: " + e.getMessage());
            lines = fallback;
        }
        Credits roll = new Credits(lines, this::showChoice);
        overlay.setOpaque(true);
        overlay.add(roll, BorderLayout.CENTER);
        overlay.revalidate();
        overlay.repaint();
        roll.start();
    }

    private void showChoice(Credits roll) {
        Theme.Card card = new Theme.Card();
        JLabel title = Theme.label(t("ending.title"), Theme.H2, Color.WHITE);
        JLabel text = Theme.label("<html><div style='width:320px'>" + t("ending.text", Fmt.num(nextMultiplier)) + "</div></html>",
                Theme.BODY, Theme.TEXT);
        JButton keep = Theme.button(t("ending.continue"), new Color(0x374151));
        JButton reborn = Theme.button(t("ending.reborn", Fmt.num(nextMultiplier)), Theme.GOLD.darker());
        keep.addActionListener(e -> close(onContinue));
        reborn.addActionListener(e -> close(onReborn));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(keep);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(reborn);
        for (JComponent c : new JComponent[]{title, text, buttons}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(c);
            card.add(Box.createVerticalStrut(14));
        }
        roll.add(card);
        roll.revalidate();
    }

    private void close(Runnable then) {
        overlay.setVisible(false);
        overlay.removeAll();
        then.run();
    }

    /** Text scrolling up over the starry background; click to skip ahead. */
    private static final class Credits extends JComponent {
        private final List<String> lines;
        private final java.util.function.Consumer<Credits> whenDone;
        private final Timer timer = new Timer(16, e -> step());
        private double scroll; // pixels scrolled so far
        private long last;
        private boolean done;

        Credits(List<String> lines, java.util.function.Consumer<Credits> whenDone) {
            this.lines = lines;
            this.whenDone = whenDone;
            setLayout(new GridBagLayout()); // centres the choice card
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    finish();
                }
            });
        }

        void start() {
            last = System.nanoTime();
            timer.start();
        }

        private int lineHeight(String s) {
            return s.startsWith("# ") ? 64 : 34;
        }

        private int totalHeight() {
            return lines.stream().mapToInt(this::lineHeight).sum();
        }

        private void step() {
            long now = System.nanoTime();
            scroll += (now - last) / 1e9 * 50; // pixels per second
            last = now;
            if (scroll > getHeight() + totalHeight()) finish();
            repaint();
        }

        private void finish() {
            if (done) return;
            done = true;
            timer.stop();
            repaint();
            whenDone.accept(this);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, getHeight(), Theme.BG));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (!done) {
                double y = getHeight() - scroll;
                for (String s : lines) {
                    boolean heading = s.startsWith("# ");
                    String text = heading ? s.substring(2) : s;
                    g.setFont(heading ? Theme.TITLE : Theme.font(Font.PLAIN, 20));
                    g.setColor(heading ? Theme.GOLD : Theme.TEXT);
                    y += lineHeight(s);
                    g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, (int) y);
                }
            }
            g.dispose();
        }
    }
}
