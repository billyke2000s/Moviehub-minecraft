package com.billyke2000s.moviehubtv.catalogue;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Orders sources so English ones come first. Uses only the labels the websites give a source
 * (its provider name and info text, for example "English", "Hindi" or "german"); "Dual"/"Multi" usually
 * include English and count as unlabelled. Most sources have
 * no label and keep their place between the two.
 */
public final class SourceLanguage {
    private SourceLanguage() {}
    public static final int ENGLISH = 2, UNKNOWN = 1, OTHER = 0;

    private static final Pattern ENGLISH_WORDS = Pattern.compile("\\b(english|eng|en)\\b");
    private static final Pattern OTHER_WORDS = Pattern.compile("\\b(hindi|tamil|telugu|malayalam|kannada|bengali|marathi|punjabi|urdu"
            + "|german|deutsch|french|francais|vf|vostfr|spanish|espanol|castellano|latino|italian|italiano|ita|portuguese|portugues"
            + "|dublado|russian|turkish|arabic|persian|korean|japanese|chinese|mandarin|cantonese|thai|vietnamese|indonesian"
            + "|filipino|tagalog|polish|dutch|swedish)\\b");

    public static int rank(JsonObject source) {
        String label = (TmdbGateway.text(source, "info") + " " + TmdbGateway.text(source, "providerName"))
                .toLowerCase(Locale.ROOT).replace('ñ', 'n').replace('ç', 'c');
        boolean english = ENGLISH_WORDS.matcher(label).find();
        boolean other = OTHER_WORDS.matcher(label).find();
        if (english && !other) return ENGLISH;
        if (other && !english) return OTHER;
        return UNKNOWN;
    }
}
