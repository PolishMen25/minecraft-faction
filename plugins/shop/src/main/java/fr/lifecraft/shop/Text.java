package fr.lifecraft.shop;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Utilitaires de texte : codes & vers Adventure, formatage de l'argent. */
final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&').hexColors().build();

    private static final DecimalFormat ENTIER;
    private static final DecimalFormat DECIMAL;

    static {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        ENTIER = new DecimalFormat("#,##0", s);
        DECIMAL = new DecimalFormat("#,##0.00", s);
    }

    private Text() {
    }

    /** Texte avec codes & en Component, sans italique par défaut (pour les items). */
    static Component item(String s) {
        return color(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    static Component color(String s) {
        return LEGACY.deserialize(s == null ? "" : s.replace('§', '&'));
    }

    /** « 1 234 $ » ou « 1 234,50 $ ». */
    static String money(double amount) {
        double r = Math.round(amount * 100.0) / 100.0;
        return (r == Math.floor(r) ? ENTIER.format(r) : DECIMAL.format(r)) + " $";
    }

    /** Montant brut pour le journal (point décimal, 2 décimales). */
    static String raw(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }
}
