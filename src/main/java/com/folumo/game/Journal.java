package com.folumo.game;

import com.folumo.tree.Fmt;
import com.folumo.tree.I18n;
import com.folumo.tree.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

import static com.folumo.tree.I18n.t;

/** The Journal card: lifetime stats, an income chart, achievements, and perks bought with Insight. */
final class Journal {
    private Journal() {}

    /**
     * @param onChange called after a perk was bought, to rebuild the card
     * @param onClose  called by the Close button
     * @param maxHeight the room the window has for the card; longer content scrolls
     */
    static JComponent build(GameState s, Runnable onChange, Runnable onClose, int maxHeight) {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        String cur = s.tree.currency;

        add(content, Theme.label(t("journal.title"), Theme.H2, Color.WHITE), 10);
        String[][] stats = {
                {t("stats.played"), I18n.duration(Math.max(1, (int) (s.playSeconds / 60)))},
                {t("stats.clicks"), Fmt.num(s.clicks)},
                {t("stats.bought"), Fmt.num(s.bought)},
                {t("stats.earned", cur), Fmt.num(s.earned)},
                {t("journal.rebirths"), Integer.toString(s.rebirths)},
                {t("journal.molecules"), Integer.toString(s.molecules)},
                {t("journal.offers"), s.offersTaken + " / " + s.offersRefused},
                {t("journal.tolerance"), Math.round(s.tolerance * 100) + "%"},
                {t("journal.offline"), t("journal.offlineValue", Fmt.num(s.offlineRate() * 100),
                        I18n.duration((int) Math.round(s.offlineHours() * 60)))}};
        StringBuilder table = new StringBuilder("<html><table cellpadding='2'>");
        for (String[] r : stats)
            table.append("<tr><td><font color='#8b93b0'>").append(esc(r[0])).append("</font></td><td><b>")
                    .append(esc(r[1])).append("</b></td></tr>");
        add(content, Theme.label(table.append("</table></html>").toString(), Theme.BODY, Color.WHITE), 14);

        add(content, Theme.label(t("journal.chart"), Theme.BODY, Theme.MUTED), 6);
        add(content, new Chart(s.history), 18);

        int have = s.achievements.size(), all = Achievement.values().length;
        add(content, Theme.label(t("journal.achievements", have, all, Fmt.num(have * GameState.ACHIEVEMENT_BONUS * 100)),
                Theme.BOLD, Theme.GOLD), 6);
        StringBuilder list = new StringBuilder("<html><div style='width:330px'>");
        for (Achievement a : Achievement.values())
            list.append(s.achievements.contains(a)
                    ? "<font color='#34d399'><font face='Dialog'>✔</font> <b>" + esc(t(a.key() + ".name")) + "</b></font> "
                    + "<font color='#8b93b0'>" + esc(t(a.key() + ".text")) + "</font><br>"
                    : "<font color='#8b93b0'><font face='Dialog'>✖</font> ???</font><br>");
        add(content, Theme.label(list.append("</div></html>").toString(), Theme.BODY, Theme.TEXT), 18);

        add(content, Theme.label(t("journal.insight", s.insight), Theme.BOLD, Theme.GOLD), 4);
        add(content, Theme.label("<html><div style='width:330px'>" + esc(t("journal.insightHelp", GameState.INSIGHT_PER_REBIRTH))
                + "</div></html>", Theme.BODY, Theme.MUTED), 8);
        for (Perk p : Perk.values()) {
            int lvl = s.perk(p);
            JLabel text = Theme.label("<html><div style='width:220px'><b>" + esc(t(p.key() + ".name")) + "</b> " + lvl + "/" + p.max
                    + "<br><font color='#8b93b0'>" + esc(t(p.key() + ".text", cur)) + "</font></div></html>", Theme.BODY, Theme.TEXT);
            JButton buy = Theme.button(lvl >= p.max ? t("journal.perkMax") : t("journal.perkBuy", p.cost(lvl)), Theme.PURPLE);
            buy.setEnabled(lvl < p.max && s.insight >= p.cost(lvl));
            buy.addActionListener(e -> {
                if (s.buyPerk(p)) onChange.run();
            });
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.add(text, BorderLayout.CENTER);
            row.add(buy, BorderLayout.EAST);
            add(content, row, 8);
        }

        JButton close = Theme.button(t("journal.close"), Theme.ACCENT);
        close.addActionListener(e -> onClose.run());
        add(content, close, 0);

        JScrollPane scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Dimension pref = content.getPreferredSize();
        // the card's own padding is 34 px; GridBagLayout shrinks anything that doesn't fit down to its minimum size
        scroll.setPreferredSize(new Dimension(pref.width + 20, Math.max(120, Math.min(pref.height, maxHeight - 34))));
        scroll.setMinimumSize(scroll.getPreferredSize());
        Theme.Card card = new Theme.Card();
        card.add(scroll);
        return card;
    }

    private static void add(JPanel p, JComponent c, int gapAfter) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(c);
        if (gapAfter > 0) p.add(Box.createVerticalStrut(gapAfter));
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Income over the minutes of play, on a log scale since idle numbers grow by powers of ten. */
    private static final class Chart extends JComponent {
        private final List<Double> values;

        Chart(List<Double> values) {
            this.values = List.copyOf(values);
            setPreferredSize(new Dimension(340, 110));
            setMaximumSize(getPreferredSize());
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.alpha(Theme.BG, 160));
            g.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 12, 12));
            g.setFont(Theme.BODY);
            if (values.size() < 2) {
                g.setColor(Theme.MUTED);
                String msg = t("journal.chartEmpty");
                g.drawString(msg, Math.max(8, (w - g.getFontMetrics().stringWidth(msg)) / 2), h / 2 + 5);
                g.dispose();
                return;
            }
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (double v : values) {
                double l = Math.log10(Math.max(v, 1e-3));
                lo = Math.min(lo, l);
                hi = Math.max(hi, l);
            }
            if (hi - lo < 1) hi = lo + 1;
            Path2D line = new Path2D.Double();
            for (int i = 0; i < values.size(); i++) {
                double x = 10 + (w - 20) * i / (double) (values.size() - 1);
                double y = h - 12 - (h - 30) * (Math.log10(Math.max(values.get(i), 1e-3)) - lo) / (hi - lo);
                if (i == 0) line.moveTo(x, y);
                else line.lineTo(x, y);
            }
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.GOLD);
            g.draw(line);
            g.setColor(Theme.MUTED);
            g.drawString(Fmt.num(Math.pow(10, hi)) + " /s", 10, 16);
            g.dispose();
        }
    }
}
