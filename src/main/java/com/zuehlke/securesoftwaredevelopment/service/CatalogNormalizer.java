package com.zuehlke.securesoftwaredevelopment.service;

import java.util.Locale;

final class CatalogNormalizer {
    private CatalogNormalizer() {
    }

    static String serviceName(String name) {
        return normalize(name);
    }

    static String partKey(String name, String manufacturer, String partNumber) {
        return normalize(name) + "|" + normalize(manufacturer) + "|" + normalize(partNumber);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
