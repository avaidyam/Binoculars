package org.kihara.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 */
public class Mailer {

    /**
     *
     *
     * @param from
     * @param to
     * @param subject
     * @param body
     * @return
     */
    public static boolean mail(String from, String to, String subject, String body) {
        try {
            Path tmp = Files.createTempFile("mail_", "_mail");
            Files.write(tmp, (body).getBytes());
            new ProcessBuilder("mailx", "-r", from, "-s", subject, to)
                    .redirectInput(tmp.toFile())
                    .start().waitFor();
            Files.deleteIfExists(tmp);

        } catch(Exception e) {
            return false;
        }; return true;
    }
}
