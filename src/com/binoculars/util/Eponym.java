package com.binoculars.util;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.*;

public class Eponym {
    private static String[] ADJECTIVES = {
            "autumn", "hidden", "bitter", "misty", "silent", "empty", "dry", "dark",
            "summer", "icy", "delicate", "quiet", "white", "cool", "spring", "winter",
            "patient", "twilight", "dawn", "crimson", "wispy", "weathered", "blue",
            "billowing", "broken", "cold", "damp", "falling", "frosty", "green",
            "long", "late", "lingering", "bold", "little", "morning", "muddy", "old",
            "red", "rough", "still", "small", "sparkling", "throbbing", "shy",
            "wandering", "withered", "wild", "black", "young", "holy", "solitary",
            "fragrant", "aged", "snowy", "proud", "floral", "restless", "divine",
            "polished", "ancient", "purple", "lively", "nameless", "lucky", "odd", "tiny",
            "free", "dry", "yellow", "orange", "gentle", "tight", "super", "royal", "broad",
            "steep", "flat", "square", "round", "mute", "noisy", "hushy", "raspy", "soft",
            "shrill", "rapid", "sweet", "curly", "calm", "jolly", "fancy", "plain", "shiny"
    };

    private static String[] NOUNS = {
            "waterfall", "river", "breeze", "moon", "rain", "wind", "sea", "morning",
            "snow", "lake", "sunset", "pine", "shadow", "leaf", "dawn", "glitter",
            "forest", "hill", "cloud", "meadow", "sun", "glade", "bird", "brook",
            "butterfly", "bush", "dew", "dust", "field", "fire", "flower", "firefly",
            "feather", "grass", "haze", "mountain", "night", "pond", "darkness",
            "snowflake", "silence", "sound", "sky", "shape", "surf", "thunder",
            "violet", "water", "wildflower", "wave", "water", "resonance", "sun",
            "wood", "dream", "cherry", "tree", "fog", "frost", "voice", "paper",
            "frog", "smoke", "star", "atom", "band", "bar", "base", "block", "boat",
            "term", "credit", "art", "fashion", "truth", "disk", "math", "unit", "cell",
            "scene", "heart", "recipe", "union", "limit", "bread", "toast", "bonus",
            "lab", "mud", "mode", "poetry", "tooth", "hall", "king", "queen", "lion", "tiger",
            "penguin", "kiwi", "cake", "mouse", "rice", "coke", "hola", "salad", "hat"
    };

    private final String delimiter;
    private String tokenChars;
    private final int tokenLength;
    private final boolean tokenHex;

    public static String eponymate(String delimiter) {
        return eponymate(delimiter, 0);
    }

    public static String eponymate(String delimiter, int tokenLength) {
        return eponymate(delimiter, "", tokenLength, true);
    }

    public static String eponymate(String delimiter, String tokenChars, int tokenLength, boolean tokenHex) {
        return new Eponym(delimiter, tokenChars, tokenLength, tokenHex).eponymate();
    }

    private Eponym(String delimiter, String tokenChars, int tokenLength, boolean tokenHex) {
        this.delimiter = delimiter;
        this.tokenChars = tokenChars;
        this.tokenLength = tokenLength;
        this.tokenHex = tokenHex;
    }

    public String eponymate() {
        Random rnd = new Random();
        String adjective = "", noun = "", token = "";
        if (tokenHex) tokenChars = "0123456789abcdef";

        adjective = ADJECTIVES[rnd.nextInt(ADJECTIVES.length)];
        noun = NOUNS[rnd.nextInt(NOUNS.length)];
        for (int i = 0; i < tokenLength; i++)
            token += tokenChars.charAt(rnd.nextInt(tokenChars.length()));

        return Arrays.asList(adjective, noun, token).stream()
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(delimiter));
    }

    /**
     * Provides a short unique identifying string.
     *
     * @return A short unique identifying string
     */
    public static String shortUUID() {
        return Long.toString(UUID.randomUUID().getLeastSignificantBits(), Character.MAX_RADIX);
    }

	/**
	 * Provides a short random number-containing string.
	 *
	 * @param digits the number of digits in length the string should be
	 * @return A short random number-containing string
	 */
    public static String randomNumber(int digits) {
		if(digits < 1) digits = 1;
		int num = (int)(random() * pow(10, digits)) + 1;
		return String.format("%0" + digits + "d", num);
    }
}
