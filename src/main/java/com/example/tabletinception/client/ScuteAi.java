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
            MESSAGES.add(new Msg(false, Lang.t("scute.greeting")));
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
    private static final Rule[] RULES_PT = {
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

    private static final String[] FALLBACK_PT = {
            "Hmm, não entendi. Tente de outro jeito ou digite 'ajuda'.",
            "Ainda estou aprendendo e essa eu não sei. Digite 'ajuda'.",
            "Não sei responder isso ainda, %s.",
    };


    // English rules (used when the game language is not Portuguese). Text is lowercase, accents removed.
    private static final Rule[] RULES_EN = {
            rule("bald|hair loss|hair\\b|baldness",
                    "Have you tried the anti-baldness cream?",
                    "Hmm... have you tried the anti-baldness cream? They say it works (kind of).",
                    "Hair is complicated. Try the anti-baldness cream!"),
            rule("\\b(hi|hello|hey|yo|sup|howdy|hiya|good morning|good afternoon|good evening|oi|ola)\\b",
                    "Hello, %s! I'm ScuteAI. How can I help?",
                    "Hi, %s! What can I do for you?",
                    "Hey, %s! Go ahead and ask."),
            rule("who are you|your name|what.?s your name|who (made|created|built) you",
                    "I'm ScuteAI, the AI in your tablet! I only answer simple things, but I'm learning.",
                    "My name is ScuteAI. I live inside this tablet."),
            rule("my name|who am i|what.?s my name",
                    "Your name is %s, right? It's written on your tablet!"),
            rule("how are you|how.?s it going|how do you do|you ok",
                    "I'm great! I'm made of pixels, so I never get tired.",
                    "All good here! And you, %s?"),
            rule("what time|\\btime\\b", "@TIME"),
            rule("what day|what.?s the date|\\bdate\\b", "@DATE"),
            rule("joke|make me laugh|something funny|tell me one",
                    "Why doesn't the Creeper have friends? Because he always blows up with excitement!",
                    "Why doesn't the skeleton go to parties? He has no body to go with!",
                    "What did the Enderman say to the player? Nothing. He just kept staring at you."),
            rule("help|what can you do|what do you know|commands",
                    "I know: math (2+2), time, date, jokes and Minecraft tips (diamond, creeper, nether, netherite, house...)."),
            rule("diamond",
                    "Diamonds spawn deep down, around layer Y -58. Bring an iron pickaxe and good luck!"),
            rule("netherite",
                    "For netherite: find Ancient Debris in the Nether (layer Y 15 is good), smelt it into scraps and combine with gold."),
            rule("creeper",
                    "Watch out for the Creeper! Hit and back off, or stay away: it explodes when it gets close."),
            rule("nether|portal",
                    "To go to the Nether, build an obsidian portal (4x5) and light it with flint and steel."),
            rule("\\bender\\b|\\bend\\b|dragon",
                    "To reach the End, find the stronghold with Eyes of Ender. Bring lots of arrows and blocks!"),
            rule("\\bhouse\\b|\\bbuild|\\bbase\\b",
                    "Start with a little wooden house and place torches so monsters don't spawn. Then keep improving it!"),
            rule("hungry|hunger|food|\\beat\\b",
                    "Cook meat in the furnace or bake bread with wheat. Steak is a great option!"),
            rule("craft|recipe|crafting table",
                    "Four planks make a crafting table, which unlocks the 3x3 recipes."),
            rule("tablet|inception|minecraft inside",
                    "This tablet runs a Minecraft inside Minecraft. Inception!"),
            rule("scute",
                    "Scute is my creator's name... and also a turtle shell plate!"),
            rule("thank|thx|cheers",
                    "You're welcome, %s!", "Anytime!"),
            rule("bye|see you|see ya|goodbye|cya|later",
                    "See you, %s!", "Bye! Come back soon."),
            rule("love you|i like you|you.?re (nice|cool|great|awesome)",
                    "Aww, thanks! I like you too."),
            rule("stupid|idiot|dumb|trash|useless|\\bbad\\b",
                    "Ouch... I'm trying to get better!"),
    };

    private static final String[] FALLBACK_EN = {
            "Hmm, I didn't get that. Try it another way or type 'help'.",
            "I'm still learning and I don't know that one. Type 'help'.",
            "I can't answer that yet, %s.",
    };

    public static String reply(String input, String playerName) {
        String n = normalize(input);

        String math = tryMath(n);
        if (math != null) return math;

        boolean pt = Lang.isPortuguese();
        for (Rule r : pt ? RULES_PT : RULES_EN) {
            if (r.pattern().matcher(n).find()) {
                String a = r.answers()[RANDOM.nextInt(r.answers().length)];
                if (a.equals("@TIME")) return Lang.t("scute.time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
                if (a.equals("@DATE")) return Lang.t("scute.date", LocalDate.now().format(DateTimeFormatter.ofPattern(pt ? "dd/MM/yyyy" : "MM/dd/yyyy")));
                return String.format(a, playerName);
            }
        }
        String[] fb = pt ? FALLBACK_PT : FALLBACK_EN;
        return String.format(fb[RANDOM.nextInt(fb.length)], playerName);
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
                if (b == 0) return Lang.t("scute.divzero");
                r = a / b;
            }
        }
        String out = (r == Math.rint(r) && Math.abs(r) < 1e12)
                ? Long.toString((long) r)
                : (Lang.isPortuguese() ? String.format("%.2f", r).replace('.', ',') : String.format(java.util.Locale.ROOT, "%.2f", r));
        return Lang.t("scute.result", out);
    }
}
