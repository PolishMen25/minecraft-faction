package fr.lifecraft.shop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** Journal des transactions (écriture sur un thread séparé pour ne pas bloquer le serveur). */
final class TransactionLog {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path file;
    private final Logger log;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LifeShop-log");
        t.setDaemon(true);
        return t;
    });

    TransactionLog(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** Format : date | joueur | ACHAT/VENTE | objet | quantité | montant. */
    void write(String player, boolean buy, String item, int qty, double amount) {
        String line = LocalDateTime.now().format(DATE) + " | " + player + " | " + (buy ? "ACHAT" : "VENTE")
            + " | " + item + " | " + qty + " | " + Text.raw(amount) + System.lineSeparator();
        io.execute(() -> {
            try {
                Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.warning("Écriture du journal impossible : " + e.getMessage());
            }
        });
    }

    void close() {
        io.shutdown();
        try {
            io.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
