package com.littlehorizons.erp.util;

final class TextColumns {
  private TextColumns() {}

  static int displayWidth(String cluster) {
    // Keep fixed-width columns aligned when descriptions use common wide glyphs.
    int first = cluster.codePointAt(0);
    boolean wideGlyph =
        (first >= 0x1100 && first <= 0x115f)
            || (first >= 0x2e80 && first <= 0xa4cf)
            || (first >= 0xac00 && first <= 0xd7a3)
            || (first >= 0xf900 && first <= 0xfaff)
            || (first >= 0xff01 && first <= 0xff60)
            || (first >= 0x1f000 && first <= 0x1faff)
            || (first >= 0x20000 && first <= 0x3fffd);
    return wideGlyph || cluster.indexOf('\ufe0f') >= 0 ? 2 : 1;
  }

}
