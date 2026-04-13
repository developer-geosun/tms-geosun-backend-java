package com.geosun.tms.auth.domain;

import java.util.Locale;

/**
 * Нормалізація email згідно ТЗ: trim та lower case для порівняння та зберігання.
 */
public final class EmailNormalizer {

  private EmailNormalizer() {}

  public static String normalize(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
