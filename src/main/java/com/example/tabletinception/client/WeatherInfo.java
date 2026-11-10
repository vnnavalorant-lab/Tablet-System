package com.example.tabletinception.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Leitura do clima do jogo (para o app Clima e para a barra de status do tablet).
 *
 * Reconhece: chuva, neve (biomas gelados), tempestade (raios), tempo nublado em biomas secos
 * (chove no mundo, mas nao cai agua), hora do dia, fase da lua, bioma, Nether e Fim.
 * Para trocar ou acrescentar comentarios, edite {@link #build(MinecraftClient)}.
 */
public final class WeatherInfo {
    public enum Kind { CLEAR, RAIN, SNOW, OVERCAST, NETHER, END, NONE }

    public enum Rating {
        GOOD("good", 0xFF55D66B),
        NEUTRAL("fair", 0xFFE6B83C),
        BAD("bad", 0xFFFF6B6B),
        NONE("none", 0xFF9AA0B8);

        private final String key;
        public final int color;

        Rating(String key, int color) {
            this.key = key;
            this.color = color;
        }

        /** Nota traduzida (BOM / REGULAR / RUIM / SEM CLIMA no pt_br). */
        public String label() {
            return Lang.t("weather.rating." + key);
        }
    }

    public Kind kind = Kind.NONE;
    public Rating rating = Rating.NONE;
    public boolean thunder;
    public String title = "";
    /** Texto curto para a barra de status. */
    public String shortLabel = "";
    public String time = "--:--";
    public long day = 1;
    /** Tick do dia (0 = amanhecer, 6000 = meio-dia, 12000 = por do sol, 18000 = meia-noite). */
    public long t = 6000;
    public int moonPhase;
    public String biome = "?";
    /** Intensidade da chuva/neve, de 0 a 1 (acompanha a transicao do jogo). */
    public float intensity;
    public boolean sheltered;
    /** Bioma gelado: o chao fica branco. */
    public boolean coldBiome;
    public boolean dryBiome;
    public final List<String> comments = new ArrayList<>();

    private static WeatherInfo cached;
    private static long cachedAt;

    // tendencia da chuva (subindo/descendo), medida a cada 500 ms
    private static float prevGradient;
    private static long prevAt;
    private static float trend;

    private WeatherInfo() {
        title = Lang.t("weather.none");
    }

    /** Versao com cache de 250 ms (barata para chamar todo frame). */
    public static synchronized WeatherInfo current(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (cached == null || now - cachedAt > 250) {
            cached = build(mc);
            cachedAt = now;
        }
        return cached;
    }

    public boolean hasPrecipitation() {
        return kind == Kind.RAIN || kind == Kind.SNOW;
    }

    public boolean isDaytime() {
        return t >= 1000 && t < 12000;
    }

    public boolean canSleep() {
        return kind != Kind.NETHER && kind != Kind.END && kind != Kind.NONE
                && (thunder || (t >= 12542 && t < 23460));
    }

    // ------------------------------------------------------------------ leitura

    private static WeatherInfo build(MinecraftClient mc) {
        WeatherInfo w = new WeatherInfo();
        ClientWorld world = mc.world;
        if (world == null) {
            w.comments.add(Lang.t("weather.no_world"));
            return w;
        }

        // ---- hora
        long tod = world.getTimeOfDay();
        w.t = ((tod % 24000L) + 24000L) % 24000L;
        w.day = tod / 24000L + 1;
        int hour = (int) ((w.t / 1000 + 6) % 24);
        int min = (int) ((w.t % 1000) * 60 / 1000);
        w.time = String.format(Locale.ROOT, "%02d:%02d", hour, min);
        w.moonPhase = world.getMoonPhase();

        // ---- local e bioma
        BlockPos pos = mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN;
        Identifier biomeId = world.getBiome(pos).getKey().map(k -> k.getValue()).orElse(null);
        String path = biomeId == null ? "" : biomeId.getPath();
        w.biome = biomeName(biomeId);
        int y = pos.getY();

        w.dryBiome = path.contains("desert") || path.contains("badlands") || path.contains("savanna");
        w.coldBiome = path.contains("snow") || path.contains("frozen") || path.contains("ice_spikes")
                || path.equals("grove") || path.equals("jagged_peaks")
                || (path.contains("windswept") && !path.contains("savanna") && y >= 125)
                || (path.contains("taiga") && y >= 165);
        w.sheltered = !world.isSkyVisible(pos);

        // ---- outras dimensoes
        if (world.getRegistryKey().equals(World.NETHER)) {
            w.kind = Kind.NETHER;
            w.rating = Rating.NONE;
            w.title = Lang.t("weather.nether");
            w.shortLabel = Lang.t("weather.nether");
            w.comments.add(Lang.t("weather.nether1"));
            w.comments.add(Lang.t("weather.nether2"));
            w.comments.add(Lang.t("weather.nether3"));
            return w;
        }
        if (world.getRegistryKey().equals(World.END)) {
            w.kind = Kind.END;
            w.rating = Rating.NONE;
            w.title = Lang.t("weather.end");
            w.shortLabel = Lang.t("weather.end");
            w.comments.add(Lang.t("weather.end1"));
            w.comments.add(Lang.t("weather.end2"));
            w.comments.add(Lang.t("weather.end3"));
            return w;
        }

        // ---- clima do mundo normal
        float rg = world.getRainGradient(1.0f);
        w.thunder = world.isThundering();
        boolean precip = rg > 0.03f;
        w.intensity = precip ? rg : 0f;

        long now = System.currentTimeMillis();
        if (now - prevAt > 2000) {            // sem historico recente
            prevGradient = rg;
            prevAt = now;
            trend = 0f;
        } else if (now - prevAt >= 500) {
            trend = rg - prevGradient;
            prevGradient = rg;
            prevAt = now;
        }

        if (precip && w.dryBiome) w.kind = Kind.OVERCAST;
        else if (precip) w.kind = w.coldBiome ? Kind.SNOW : Kind.RAIN;
        else w.kind = Kind.CLEAR;

        boolean night = w.t >= 13000 && w.t < 23000;
        boolean dawn = w.t >= 23000 || w.t < 1000;
        boolean dusk = w.t >= 12000 && w.t < 13000;
        boolean day = !night && !dawn && !dusk;
        boolean rising = trend > 0.0005f && rg < 0.95f;
        boolean falling = trend < -0.0005f && rg < 0.95f;

        switch (w.kind) {
            case CLEAR -> {
                w.rating = Rating.GOOD;
                if (night) {
                    w.title = Lang.t("weather.clear_night");
                    w.comments.add(Lang.t("weather.clear_night1"));
                    w.comments.add(w.canSleep()
                            ? Lang.t("weather.can_sleep")
                            : Lang.t("weather.can_sleep_soon"));
                    if (w.moonPhase == 0) w.comments.add(Lang.t("weather.full_moon"));
                    else if (w.moonPhase == 4) w.comments.add(Lang.t("weather.new_moon"));
                } else if (dawn) {
                    w.title = Lang.t("weather.dawn");
                    w.comments.add(Lang.t("weather.dawn1"));
                    w.comments.add(Lang.t("weather.dawn2"));
                } else if (dusk) {
                    w.title = Lang.t("weather.dusk");
                    w.comments.add(Lang.t("weather.dusk1"));
                    w.comments.add(Lang.t("weather.dusk2"));
                } else {
                    w.title = Lang.t("weather.sunny");
                    w.comments.add(Lang.t("weather.sunny1"));
                    w.comments.add(Lang.t("weather.sunny2"));
                    w.comments.add(Lang.t("weather.sunny3"));
                }
                if (w.dryBiome) w.comments.add(Lang.t("weather.dry"));
                if (w.coldBiome) w.comments.add(Lang.t("weather.cold"));
                w.shortLabel = w.title;
            }
            case RAIN -> {
                if (w.thunder) {
                    w.rating = Rating.BAD;
                    w.title = Lang.t("weather.storm");
                    w.shortLabel = Lang.t("weather.storm");
                    thunderComments(w);
                } else {
                    w.rating = Rating.BAD;
                    w.title = rising ? Lang.t("weather.rain_start") : falling ? Lang.t("weather.rain_ease") : Lang.t("weather.rain");
                    w.shortLabel = Lang.t("weather.rain");
                    w.comments.add(Lang.t("weather.rain1"));
                    if (rising) w.comments.add(Lang.t("weather.rain2"));
                    if (falling) w.comments.add(Lang.t("weather.rain3"));
                    w.comments.add(Lang.t("weather.rain4"));
                    w.comments.add(Lang.t("weather.rain5"));
                    if (night) w.comments.add(Lang.t("weather.rain_night"));
                    else if (w.isDaytime()) w.comments.add(Lang.t("weather.rain_day"));
                    w.comments.add(w.sheltered
                            ? Lang.t("weather.rain_roof")
                            : Lang.t("weather.rain_open"));
                }
            }
            case SNOW -> {
                if (w.thunder) {
                    w.rating = Rating.BAD;
                    w.title = Lang.t("weather.snowstorm");
                    w.shortLabel = Lang.t("weather.snowstorm_short");
                    thunderComments(w);
                } else {
                    w.rating = Rating.NEUTRAL;
                    w.title = rising ? Lang.t("weather.snow_start") : falling ? Lang.t("weather.snow_ease") : Lang.t("weather.snow");
                    w.shortLabel = Lang.t("weather.snow");
                    w.comments.add(Lang.t("weather.snow1"));
                    w.comments.add(Lang.t("weather.snow2"));
                    w.comments.add(Lang.t("weather.snow3"));
                    w.comments.add(Lang.t("weather.snow4"));
                    w.comments.add(w.sheltered
                            ? Lang.t("weather.snow_roof")
                            : Lang.t("weather.snow_open"));
                }
            }
            case OVERCAST -> {
                w.rating = Rating.NEUTRAL;
                w.title = Lang.t("weather.overcast");
                w.shortLabel = Lang.t("weather.overcast");
                w.comments.add(Lang.t("weather.overcast1"));
                if (w.isDaytime()) w.comments.add(Lang.t("weather.overcast2"));
                w.comments.add(Lang.t("weather.overcast3"));
                if (w.thunder) w.comments.add(Lang.t("weather.overcast4"));
            }
            default -> { }
        }

        if (w.sheltered && y < 55) {
            w.comments.add(Lang.t("weather.underground", y));
        }
        return w;
    }

    private static void thunderComments(WeatherInfo w) {
        w.comments.add(Lang.t("weather.thunder1"));
        w.comments.add(Lang.t("weather.thunder2"));
        w.comments.add(Lang.t("weather.thunder3"));
        w.comments.add(Lang.t("weather.thunder4"));
        w.comments.add(Lang.t("weather.thunder5"));
        w.comments.add(w.sheltered
                ? Lang.t("weather.thunder_roof")
                : Lang.t("weather.thunder_open"));
    }

    private static String biomeName(Identifier id) {
        if (id == null) return "?";
        String key = "biome." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        String translated = Text.translatable(key).getString();
        if (!translated.equals(key)) return translated;
        StringBuilder sb = new StringBuilder();
        for (String part : id.getPath().split("[_/]")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
