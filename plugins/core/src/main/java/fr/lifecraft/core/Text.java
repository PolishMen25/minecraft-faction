package fr.lifecraft.core;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Utilitaires de texte : codes & vers Adventure, formatage de l'argent. */
final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&').hexColors().build();

    private static final DecimalFormat MONEY;

    static {
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        MONEY = new DecimalFormat("#,##0", s);
    }

    private Text() {
    }

    /** Convertit un texte avec codes & (ou §) en Component. */
    static Component color(String s) {
        return LEGACY.deserialize(s == null ? "" : s.replace('§', '&'));
    }

    /** Plusieurs lignes jointes par des retours à la ligne. */
    static Component lines(List<String> lines) {
        return color(String.join("\n", lines));
    }

    /** « 1 234 $ » (les centimes sont ignorés). */
    static String money(double amount) {
        return MONEY.format(Math.floor(amount)) + " $";
    }
}
