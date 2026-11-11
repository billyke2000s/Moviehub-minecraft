package com.billyke2000s.moviehubtv.client;

/**
 * Positions on the guide canvas for a given canvas width. The canvas is always GUIDE_HEIGHT (160) tall and
 * at least 192 wide; wider TVs get a wider guide, and when there is room the programmes are shown as
 * separate Now / Next / Later columns. Pure maths, so it can be tested on its own.
 */
final class GuideLayout {
    static final float MIN_WIDTH = 192;
    static final float CH_X = 2;
    static final float CH_W = 60;
    static final float PROG_X = 64;
    static final float GAP = 2;
    /** Programme area needed for 2 and 3 separate columns. */
    static final float TWO_COLUMNS = 200;
    static final float THREE_COLUMNS = 330;
    static final float BUTTON_W = 18;
    static final float BACK_W = 44;
    static final float CATEGORIES_W = 46;

    final float width;
    final float progW;
    final int columns;

    GuideLayout(float canvasWidth) {
        this.width = Math.max(MIN_WIDTH, canvasWidth);
        this.progW = width - PROG_X - 2;
        this.columns = progW >= THREE_COLUMNS ? 3 : progW >= TWO_COLUMNS ? 2 : 1;
    }

    /** Width of one programme column (the whole programme area when there is one column). */
    float columnW() { return (progW - GAP * (columns - 1)) / columns; }

    float columnX(int column) { return PROG_X + column * (columnW() + GAP); }

    /** Right edge of the row (channel cell to the last programme column). */
    float rowRight() { return PROG_X + progW; }

    float backX() { return width - 2 - BACK_W; }

    float categoriesX() { return backX() - 2 - CATEGORIES_W; }

    /** Space for the "101-107 of 2227" text between the arrow buttons and the Categories button. */
    float pageX() { return 3 + 2 * (BUTTON_W + 2) + 3; }

    float pageW() { return Math.max(0, categoriesX() - 3 - pageX()); }
}
