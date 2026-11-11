package com.billyke2000s.moviehubtv.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Keyboard entry for the existing on-TV menu. Playback continues while typing. */
final class CatalogueSearchScreen extends Screen {
    private final BlockPos pos;
    private EditBox input;
    private String status = "Enter a title, or tmdb:movie:550 / tmdb:tv:1399";
    CatalogueSearchScreen(BlockPos pos) { super(Component.literal("Search free Movies / Shows / Anime")); this.pos = pos.immutable(); }
    protected void init() {
        int w = Math.min(340, width - 30), x = (width - w) / 2;
        input = new EditBox(font, x, height / 2 - 18, w, 20, Component.literal("Title or TMDB ID"));
        input.setMaxLength(100); addRenderableWidget(input); setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Search"), b -> submit()).bounds(x, height / 2 + 10, w / 2 - 4, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(x + w / 2 + 4, height / 2 + 10, w / 2 - 4, 20).build());
    }
    private void submit() {
        String value = input.getValue().trim();
        if (!value.startsWith("tmdb:")) { GuideRenderer.search(pos, value); onClose(); return; }
        String[] p = value.split(":");
        if (p.length != 3 || (!p[1].equals("movie") && !p[1].equals("tv"))) { status = "Use tmdb:movie:ID or tmdb:tv:ID"; return; }
        int id;
        try { id = Integer.parseInt(p[2]); if (id < 1) throw new NumberFormatException(); }
        catch (NumberFormatException e) { status = "Enter a positive numeric TMDB ID"; return; }
        status = "Looking up TMDB...";
        CatalogueClient.request(p[1], id, data -> {
            if (minecraft == null || minecraft.screen != this) return;
            if (data.has("error")) { status = data.get("error").getAsString(); return; }
            String title = VodCatalogue.text(data, "title");
            if (title.isEmpty()) { status = "No title found for that ID"; return; }
            // TMDB IDs identify metadata only. Match against the actual free regional catalogue.
            String original = VodCatalogue.text(data, "original");
            String match = VodRepository.all().stream().filter(i ->
                    i.series() == p[1].equals("tv") && (VodCatalogue.normal(i.title()).equals(VodCatalogue.normal(title))
                    || (!original.isEmpty() && VodCatalogue.normal(i.title()).equals(VodCatalogue.normal(original)))))
                    .map(VodCatalogue.Item::title).findFirst().orElse(null);
            if (match == null) { status = title + ": no free Pluto source in your region. Try a title search."; return; }
            GuideRenderer.chooseTab(pos, p[1].equals("movie") ? 1 : 2);
            GuideRenderer.search(pos, match); onClose();
        });
    }
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 257 || key == 335) { submit(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(Math.max(5, width / 2 - 190), height / 2 - 64, Math.min(width - 5, width / 2 + 190), height - 8, 0xE00A1630);
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, height / 2 - 52, 0xFFFFC83D);
        int y = height / 2 + 40;
        for (var line : font.split(Component.literal(status), Math.min(360, width - 30))) {
            g.drawCenteredString(font, line, width / 2, y, 0xFFA9BCDD); y += 10;
        }
        g.drawCenteredString(font, "TMDB metadata; free playback from Pluto TV", width / 2, height - 22, 0xFFA9BCDD);
    }
    public boolean isPauseScreen() { return false; }
}
