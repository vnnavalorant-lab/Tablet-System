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
        GOOD("BOM", 0xFF55D66B),
        NEUTRAL("REGULAR", 0xFFE6B83C),
        BAD("RUIM", 0xFFFF6B6B),
        NONE("SEM CLIMA", 0xFF9AA0B8);

        public final String label;
        public final int color;

        Rating(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public Kind kind = Kind.NONE;
    public Rating rating = Rating.NONE;
    public boolean thunder;
    public String title = "Sem sinal";
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

    private WeatherInfo() { }

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
            w.comments.add("Não consegui falar com o mundo agora. Tente de novo em instantes.");
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
            w.title = "Nether";
            w.shortLabel = "Nether";
            w.comments.add("Aqui não existe sol, chuva nem noite: só calor, lava e fogo.");
            w.comments.add("Não durma aqui: a cama explode!");
            w.comments.add("O clima do mundo normal não aparece daqui. Volte à superfície para consultar.");
            return w;
        }
        if (world.getRegistryKey().equals(World.END)) {
            w.kind = Kind.END;
            w.rating = Rating.NONE;
            w.title = "O Fim";
            w.shortLabel = "O Fim";
            w.comments.add("No Fim não há clima, dia ou noite: só o vazio e o Dragão.");
            w.comments.add("Camas também explodem aqui. Nem tente dormir.");
            w.comments.add("Cuidado com os Endermen e com a queda no vazio.");
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
                    w.title = "Noite limpa";
                    w.comments.add("Céu limpo e estrelado, mas é hora dos monstros. Fique perto de um abrigo.");
                    w.comments.add(w.canSleep()
                            ? "Você já pode dormir para pular a noite."
                            : "Daqui a pouco você poderá dormir.");
                    if (w.moonPhase == 0) w.comments.add("Lua cheia: a noite está mais clara que o normal.");
                    else if (w.moonPhase == 4) w.comments.add("Lua nova: a noite está bem escura. Leve tochas.");
                } else if (dawn) {
                    w.title = "Amanhecer";
                    w.comments.add("O sol está nascendo e o céu está limpo. Ótimo começo de dia!");
                    w.comments.add("Os monstros da noite começam a sumir. Já é seguro sair do abrigo.");
                } else if (dusk) {
                    w.title = "Entardecer";
                    w.comments.add("O céu está limpo, mas o sol está se pondo. Procure um abrigo.");
                    w.comments.add("Acenda tochas: daqui a pouco os monstros começam a aparecer.");
                } else {
                    w.title = "Ensolarado";
                    w.comments.add("Céu limpo e sem nuvens carregadas. O clima está ótimo!");
                    w.comments.add("Bom momento para explorar, construir ou plantar na superfície.");
                    w.comments.add("Zumbis e esqueletos pegam fogo ao sol. Creepers e aranhas continuam por aí.");
                }
                if (w.dryBiome) w.comments.add("Bioma seco: aqui nunca chove nem neva.");
                if (w.coldBiome) w.comments.add("Bioma gelado: quando o tempo fechar, vai nevar em vez de chover.");
                w.shortLabel = w.title;
            }
            case RAIN -> {
                if (w.thunder) {
                    w.rating = Rating.BAD;
                    w.title = "Tempestade";
                    w.shortLabel = "Tempestade";
                    thunderComments(w);
                } else {
                    w.rating = Rating.BAD;
                    w.title = rising ? "Começando a chover" : falling ? "Chuva diminuindo" : "Chuva";
                    w.shortLabel = "Chuva";
                    w.comments.add("O céu está fechado e chovendo. O clima está ruim para passear.");
                    if (rising) w.comments.add("A chuva está começando agora e vai ficar mais forte.");
                    if (falling) w.comments.add("A chuva está diminuindo. Logo o tempo deve abrir.");
                    w.comments.add("A chuva apaga fogo e rega as plantações sozinha.");
                    w.comments.add("Pescar rende mais durante a chuva.");
                    if (night) w.comments.add("Chuva à noite: pouca visibilidade e monstros soltos. Fique atento!");
                    else if (w.isDaytime()) w.comments.add("Com chuva, zumbis e esqueletos não queimam ao sol, nem de dia.");
                    w.comments.add(w.sheltered
                            ? "Você está sob um teto, então a chuva não te alcança."
                            : "Você está ao ar livre, mas a chuva não causa dano.");
                }
            }
            case SNOW -> {
                if (w.thunder) {
                    w.rating = Rating.BAD;
                    w.title = "Tempestade de neve";
                    w.shortLabel = "Nevasca";
                    thunderComments(w);
                } else {
                    w.rating = Rating.NEUTRAL;
                    w.title = rising ? "Começando a nevar" : falling ? "Neve diminuindo" : "Nevando";
                    w.shortLabel = "Nevando";
                    w.comments.add("Está nevando por aqui: tudo fica branquinho e bonito, mas faz frio.");
                    w.comments.add("Neve em pó é armadilha: você afunda e congela. Use botas de couro.");
                    w.comments.add("Esqueletos congelados (strays) aparecem nesses biomas e atiram flechas de lentidão.");
                    w.comments.add("A água congela e a neve se acumula no chão.");
                    w.comments.add(w.sheltered
                            ? "Você está sob um teto, protegido da neve."
                            : "Você está ao ar livre: leve comida e tochas.");
                }
            }
            case OVERCAST -> {
                w.rating = Rating.NEUTRAL;
                w.title = "Nublado";
                w.shortLabel = "Nublado";
                w.comments.add("O mundo está chovendo, mas este bioma é seco: o céu fecha e nenhuma gota cai.");
                if (w.isDaytime()) w.comments.add("Mesmo assim, zumbis e esqueletos não queimam com o tempo fechado.");
                w.comments.add("Para ver chuva de verdade, vá a uma planície ou floresta.");
                if (w.thunder) w.comments.add("Há trovões no mundo. Raios podem cair em outros biomas.");
            }
            default -> { }
        }

        if (w.sheltered && y < 55) {
            w.comments.add("Você está no subsolo (Y " + y + "): o clima lá fora não afeta as cavernas.");
        }
        return w;
    }

    private static void thunderComments(WeatherInfo w) {
        w.comments.add("Tempestade! Raios podem cair a qualquer momento.");
        w.comments.add("Evite campo aberto, colinas e topo de torres: raios acertam o ponto mais alto.");
        w.comments.add("Raios incendeiam madeira, criam creepers carregados e transformam aldeões em bruxas.");
        w.comments.add("Cuidado com cavalos solitários: tempestades podem criar armadilhas de esqueletos.");
        w.comments.add("Você pode dormir agora: dormir encerra a tempestade.");
        w.comments.add(w.sheltered
                ? "Você está protegido por um teto."
                : "Você está exposto ao céu: procure abrigo!");
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
