package dev.achiri.multivault.common.util;

import java.text.Normalizer;

public class SlugUtils {
    public static String toSlug(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")          // elimina acentos
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")     // reemplaza caracteres especiales
                .replaceAll("^_+|_+$", "");        // elimina '_' al inicio y final
    }
}