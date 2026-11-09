package com.example.tabletinception.client;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ScuteAI: assistente simples do tablet. Nao e uma IA de verdade: so procura palavras-chave
 * na mensagem e devolve uma resposta pronta. O historico fica guardado enquanto o jogo estiver aberto.
 *
 * Para adicionar respostas novas, basta incluir uma linha em {@link #RULES}.
 */
public final class ScuteAi {
    public static final String NAME = "ScuteAI";
    public static final int MAX_INPUT = 80;
    private static final int MAX_MESSAGES = 100;

    public record Msg(boolean fromPlayer, String text) { }

    private record Rule(Pattern pattern, String[] answers) { }

    private static final Random RANDOM = new Random();
    private static final List<Msg> MESSAGES = new ArrayList<>();
    private static final Pattern MATH = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*([+\\-*x/:])\\s*(-?\\d+(?:[.,]\\d+)?)");

    // resposta que ainda vai "ser digitada"
    private static String pending = null;
    private static long pendingAt = 0;

    private ScuteAi() { }

    // ------------------------------------------------------------------ historico

    public static List<Msg> messages() {
        if (MESSAGES.isEmpty()) {
            MESSAGES.add(new Msg(false, "Olá! Eu sou o ScuteAI. Pergunte alguma coisa ou digite 'ajuda'."));
        }
        return MESSAGES;
    }

    public static boolean isTyping() {
        return pending != null;
    }

    /** Envia a mensagem do jogador e agenda a resposta. */
    public static void send(String text, String playerName) {
        text = text.trim();
        if (text.isEmpty()) return;
        messages();
        add(new Msg(true, text));
        if (pending != null) { // ja estava respondendo: entrega a anterior na hora
            add(new Msg(false, pending));
        }
        pending = reply(text, playerName);
        long delay = Math.min(1800, 600 + 25L * pending.length());
        pendingAt = System.currentTimeMillis() + delay;
    }

    /** Deve ser chamado todo frame: entrega a resposta quando o tempo de "digitando" acabar. */
    public static void tick() {
        if (pending != null && System.currentTimeMillis() >= pendingAt) {
            add(new Msg(false, pending));
            pending = null;
        }
    }

    private static void add(Msg m) {
        MESSAGES.add(m);
        while (MESSAGES.size() > MAX_MESSAGES) MESSAGES.remove(0);
    }

    // ------------------------------------------------------------------ respostas

    private static Rule rule(String regex, String... answers) {
        return new Rule(Pattern.compile(regex), answers);
    }

    // Texto normalizado: minusculo e sem acentos. %s = nome do jogador. Primeira regra que casar vence.
    private static final Rule[] RULES = {
            rule("calv|cabelo|careca|queda de cabelo",
                    "Já tentou o creme anti-calvície?",
                    "Hmm... já tentou o creme anti-calvície? Dizem que funciona (mais ou menos).",
                    "Cabelo é complicado. Experimente o creme anti-calvície!"),
            rule("\\b(oi|ola|eai|e ai|opa|salve|fala|hello|hey|oie|bom dia|boa tarde|boa noite)\\b",
                    "Olá, %s! Eu sou o ScuteAI. Em que posso ajudar?",
                    "Oi, %s! Como posso ajudar?",
                    "E aí, %s! Pode perguntar."),
            rule("quem (e|eh) (voce|vc)|seu nome|como (voce )?se chama|quem criou",
                    "Eu sou o ScuteAI, a IA do seu tablet! Respondo coisas simples, mas estou aprendendo.",
                    "Meu nome é ScuteAI. Moro dentro deste tablet."),
            rule("meu nome|me chamo|qual (e )?meu nome|quem sou eu",
                    "Seu nome é %s, né? Está escrito no seu tablet!"),
            rule("como (voce )?(vai|esta|ta)\\b|tudo bem|tudo bom|como (vc )?(vai|ta)",
                    "Estou ótimo! Sou feito de pixels, então nunca fico cansado.",
                    "Tudo bem por aqui! E com você, %s?"),
            rule("que horas|\\bhoras?\\b", "@TIME"),
            rule("que dia|\\bdata\\b|qual (e )?a data", "@DATE"),
            rule("piada|me faz rir|conte uma|algo engracado",
                    "Por que o Creeper não tem amigos? Porque ele sempre explode de emoção!",
                    "Por que o esqueleto não vai às festas? Porque ele não tem corpo pra isso!",
                    "O que o Enderman disse ao jogador? Nada. Ele só ficou te encarando."),
            rule("ajuda|help|o que (voce|vc) (faz|sabe|pode)|comandos",
                    "Eu sei: contas (2+2), horas, data, piadas e dicas de Minecraft (diamante, creeper, nether, netherite, casa...)."),
            rule("diamante",
                    "Diamantes aparecem bem fundo, perto da camada Y -58. Leve uma picareta de ferro e boa sorte!"),
            rule("netherite",
                    "Para o netherite: ache Detritos Antigos no Nether (a camada Y 15 é boa), funda em lingotes e combine com ouro."),
            rule("creeper",
                    "Cuidado com o Creeper! Bata e recue, ou fique longe: ele explode quando chega perto."),
            rule("nether|portal",
                    "Para ir ao Nether, monte um portal de obsidiana (4x5) e acenda com isqueiro."),
            rule("\\bender\\b|\\bend\\b|dragao",
                    "Para chegar ao End, ache a fortaleza com Olhos de Ender. Leve muitas flechas e blocos!"),
            rule("\\bcasa\\b|construi|construcao|\\bbase\\b",
                    "Comece com uma casinha de madeira e coloque tochas pra não nascerem monstros. Depois vá melhorando!"),
            rule("fome|comida|comer",
                    "Cozinhe carne na fornalha ou faça pão com trigo. Bife é uma ótima opção!"),
            rule("craft|receita|mesa de trabalho",
                    "Quatro tábuas formam uma mesa de trabalho, que libera as receitas 3x3."),
            rule("tablet|inception|minecraft dentro",
                    "Este tablet roda um Minecraft dentro do Minecraft. Inception!"),
            rule("scute",
                    "Scute é o nome do meu criador... e também uma escama de tartaruga!"),
            rule("obrigad|valeu|vlw|brigad",
                    "De nada, %s!", "Disponha!"),
            rule("tchau|ate mais|ate logo|flw|falou|adeus",
                    "Até mais, %s!", "Tchau! Volte sempre."),
            rule("te amo|gosto de voce|voce e legal|voce e demais",
                    "Aww, obrigado! Eu também gosto de você."),
            rule("burro|idiota|lixo|ruim|inutil",
                    "Poxa... estou tentando melhorar!"),
    };

    private static final String[] FALLBACK = {
            "Hmm, não entendi. Tente de outro jeito ou digite 'ajuda'.",
            "Ainda estou aprendendo e essa eu não sei. Digite 'ajuda'.",
            "Não sei responder isso ainda, %s.",
    };

    public static String reply(String input, String playerName) {
        String n = normalize(input);

        String math = tryMath(n);
        if (math != null) return math;

        for (Rule r : RULES) {
            if (r.pattern().matcher(n).find()) {
                String a = r.answers()[RANDOM.nextInt(r.answers().length)];
                if (a.equals("@TIME")) return "Agora são " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ".";
                if (a.equals("@DATE")) return "Hoje é " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".";
                return String.format(a, playerName);
            }
        }
        return String.format(FALLBACK[RANDOM.nextInt(FALLBACK.length)], playerName);
    }

    private static String normalize(String s) {
        String t = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return t.toLowerCase().trim();
    }

    private static String tryMath(String n) {
        Matcher m = MATH.matcher(n);
        if (!m.find()) return null;
        double a = Double.parseDouble(m.group(1).replace(',', '.'));
        double b = Double.parseDouble(m.group(3).replace(',', '.'));
        double r;
        switch (m.group(2)) {
            case "+" -> r = a + b;
            case "-" -> r = a - b;
            case "*", "x" -> r = a * b;
            default -> {
                if (b == 0) return "Não dá pra dividir por zero!";
                r = a / b;
            }
        }
        String out = (r == Math.rint(r) && Math.abs(r) < 1e12)
                ? Long.toString((long) r)
                : String.format("%.2f", r).replace('.', ',');
        return "O resultado é " + out + ".";
    }
}
