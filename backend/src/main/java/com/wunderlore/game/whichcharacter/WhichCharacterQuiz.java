package com.wunderlore.game.whichcharacter;

import java.util.List;
import java.util.Map;

public final class WhichCharacterQuiz {

    private WhichCharacterQuiz() {
    }

    public static final List<QuizQuestion> QUESTIONS = List.of(

            // Question 1 — Facing a crisis
            question("q01",
                    "When everything around you starts falling apart, what is your first instinct?",
                    option("q01_a", "Take responsibility and find a way forward, even if the burden falls on you.",
                            CharacterId.FRODO, CharacterId.SAM),
                    option("q01_b", "Make sure everyone is safe and supported before anything else.",
                            CharacterId.SAM, CharacterId.FRODO),
                    option("q01_c", "Step forward, organize people, and lead them through the crisis.",
                            CharacterId.ARAGORN, CharacterId.BOROMIR),
                    option("q01_d", "Look beyond the immediate problem and search for the deeper cause.",
                            CharacterId.GANDALF, CharacterId.GALADRIEL)),

            // Question 2 — Trust
            question("q02",
                    "When someone you barely know asks for your trust, how do you respond?",
                    option("q02_a", "I remain cautious and look for signs of their true intentions.",
                            CharacterId.SAURON, CharacterId.GANDALF),
                    option("q02_b", "I want to believe them, even when my own judgment tells me to be careful.",
                            CharacterId.GOLLUM, CharacterId.FRODO),
                    option("q02_c", "I observe their actions before deciding whether they deserve my trust.",
                            CharacterId.LEGOLAS, CharacterId.ARAGORN),
                    option("q02_d", "I give them a chance, but I expect honesty and loyalty in return.",
                            CharacterId.GIMLI, CharacterId.SAM)),

            // Question 3 — Ambition
            question("q03",
                    "What motivates you most when you set yourself a difficult goal?",
                    option("q03_a", "The chance to accomplish something meaningful, even if it requires sacrifice.",
                            CharacterId.GALADRIEL, CharacterId.GANDALF),
                    option("q03_b", "The opportunity to prove that I am capable of more than others expect.",
                            CharacterId.BOROMIR, CharacterId.ARAGORN),
                    option("q03_c", "The desire to protect something or someone I deeply care about.",
                            CharacterId.FRODO, CharacterId.SAM),
                    option("q03_d", "The satisfaction of achieving something together with people I trust.",
                            CharacterId.SAM, CharacterId.GIMLI)),

            // Question 4 — Uncertainty
            question("q04",
                    "You must make an important decision, but you have very little information. What do you do?",
                    option("q04_a", "Trust my instincts and take action rather than remain paralyzed.",
                            CharacterId.ARAGORN, CharacterId.BOROMIR),
                    option("q04_b", "Wait, observe, and gather as much information as possible.",
                            CharacterId.GANDALF, CharacterId.LEGOLAS),
                    option("q04_c", "Consider the consequences carefully and avoid unnecessary risks.",
                            CharacterId.SAURON, CharacterId.GALADRIEL),
                    option("q04_d", "Choose the path that feels right, even if I cannot explain why.",
                            CharacterId.GOLLUM, CharacterId.FRODO)),

            // Question 5 — Conflict
            question("q05",
                    "When someone strongly disagrees with you, what is your usual reaction?",
                    option("q05_a", "I stay calm and try to understand the bigger picture.",
                            CharacterId.LEGOLAS, CharacterId.GANDALF),
                    option("q05_b", "I defend my position directly and refuse to back down easily.",
                            CharacterId.GIMLI, CharacterId.BOROMIR),
                    option("q05_c", "I look for a solution that preserves everyone's dignity.",
                            CharacterId.GALADRIEL, CharacterId.SAM),
                    option("q05_d", "I try to understand what is really driving the disagreement.",
                            CharacterId.BOROMIR, CharacterId.SAURON)),

            // Question 6 — Temptation
            question("q06",
                    "You are offered something you deeply desire, but accepting it could change who you are. What do you do?",
                    option("q06_a", "Refuse it, even if giving it up is painful.",
                            CharacterId.FRODO, CharacterId.GALADRIEL),
                    option("q06_b", "Think about whether I could use it responsibly for a greater purpose.",
                            CharacterId.SAM, CharacterId.GANDALF),
                    option("q06_c", "Consider whether the opportunity is too important to let go.",
                            CharacterId.ARAGORN, CharacterId.BOROMIR),
                    option("q06_d", "Question why I want it so badly and what it might cost me.",
                            CharacterId.GANDALF, CharacterId.LEGOLAS)),

            // Question 7 — Your role in a group
            question("q07",
                    "When working with a group, which role comes most naturally to you?",
                    option("q07_a", "The strategist who thinks several steps ahead.",
                            CharacterId.SAURON, CharacterId.GANDALF),
                    option("q07_b", "The one who notices details others tend to overlook.",
                            CharacterId.GOLLUM, CharacterId.LEGOLAS),
                    option("q07_c", "The dependable person who keeps everyone motivated.",
                            CharacterId.LEGOLAS, CharacterId.SAM),
                    option("q07_d", "The practical teammate who brings energy and determination.",
                            CharacterId.GIMLI, CharacterId.ARAGORN)),

            // Question 8 — Failure
            question("q08",
                    "When something important goes wrong because of a decision you made, how do you react?",
                    option("q08_a", "I accept responsibility and try to make things right.",
                            CharacterId.GALADRIEL, CharacterId.ARAGORN),
                    option("q08_b", "I feel the weight of the failure, but I keep moving forward.",
                            CharacterId.BOROMIR, CharacterId.FRODO),
                    option("q08_c", "I analyze what happened so I can avoid the same mistake.",
                            CharacterId.FRODO, CharacterId.GANDALF),
                    option("q08_d", "I focus on supporting the people affected by the situation.",
                            CharacterId.SAM, CharacterId.GIMLI)),

            // Question 9 — Change
            question("q09",
                    "How do you feel when life forces you into an unfamiliar situation?",
                    option("q09_a", "I adapt and take the opportunity to grow into something new.",
                            CharacterId.ARAGORN, CharacterId.LEGOLAS),
                    option("q09_b", "I prefer to understand the situation before making any changes.",
                            CharacterId.GANDALF, CharacterId.GALADRIEL),
                    option("q09_c", "I resist losing what is familiar and important to me.",
                            CharacterId.SAURON, CharacterId.GOLLUM),
                    option("q09_d", "I feel conflicted, but eventually follow the path that seems necessary.",
                            CharacterId.GOLLUM, CharacterId.FRODO)),

            // Question 10 — Responsibility
            question("q10",
                    "If you were given a responsibility that could affect many people's lives, what would matter most to you?",
                    option("q10_a", "Making the difficult decisions that others are unwilling to make.",
                            CharacterId.LEGOLAS, CharacterId.ARAGORN),
                    option("q10_b", "Ensuring that nobody is abandoned along the way.",
                            CharacterId.GIMLI, CharacterId.SAM),
                    option("q10_c", "Using wisdom and experience to guide people toward the right direction.",
                            CharacterId.GALADRIEL, CharacterId.GANDALF),
                    option("q10_d", "Protecting what matters, even if the responsibility becomes a personal burden.",
                            CharacterId.BOROMIR, CharacterId.FRODO)));

    public static final Map<CharacterId, CharacterResult> CHARACTERS = Map.ofEntries(

            Map.entry(CharacterId.FRODO,
                    new CharacterResult(CharacterId.FRODO, "Frodo",
                            "You carry difficult responsibilities with courage, even when the journey becomes overwhelming. Your strength lies in perseverance, empathy, and the willingness to sacrifice for something greater than yourself.")),

            Map.entry(CharacterId.SAM,
                    new CharacterResult(CharacterId.SAM, "Sam",
                            "You are dependable, compassionate, and fiercely loyal. You believe that small acts of kindness matter, and you rarely abandon the people who count on you.")),

            Map.entry(CharacterId.ARAGORN,
                    new CharacterResult(CharacterId.ARAGORN, "Aragorn",
                            "You lead through responsibility rather than recognition. Independent and thoughtful, you understand that true leadership means serving others and accepting difficult choices.")),

            Map.entry(CharacterId.GANDALF,
                    new CharacterResult(CharacterId.GANDALF, "Gandalf",
                            "You value wisdom, perspective, and patience. You tend to look beyond immediate problems and help others discover their own strength rather than controlling every outcome.")),

            Map.entry(CharacterId.SAURON,
                    new CharacterResult(CharacterId.SAURON, "Sauron",
                            "You are strategic, ambitious, and intensely focused on your goals. You value control and careful planning, and you prefer to understand every moving part before taking action.")),

            Map.entry(CharacterId.GOLLUM,
                    new CharacterResult(CharacterId.GOLLUM, "Gollum",
                            "You experience things deeply and can find yourself torn between competing desires. Your inner world is complex, and your strongest struggles often reveal what matters most to you.")),

            Map.entry(CharacterId.LEGOLAS,
                    new CharacterResult(CharacterId.LEGOLAS, "Legolas",
                            "You are observant, composed, and adaptable. You notice details others miss, remain calm under pressure, and appreciate different perspectives.")),

            Map.entry(CharacterId.GIMLI,
                    new CharacterResult(CharacterId.GIMLI, "Gimli",
                            "You are determined, straightforward, and loyal. You value friendship, stand firmly behind your convictions, and bring energy and persistence to whatever you undertake.")),

            Map.entry(CharacterId.GALADRIEL,
                    new CharacterResult(CharacterId.GALADRIEL, "Galadriel",
                            "You combine wisdom with self-control and a strong sense of purpose. You understand the importance of difficult choices and believe that true strength includes knowing when to exercise restraint.")),

            Map.entry(CharacterId.BOROMIR,
                    new CharacterResult(CharacterId.BOROMIR, "Boromir",
                            "You are courageous, protective, and deeply committed to those you care about. You feel responsibility intensely, and sometimes struggle with the tension between ambition and doing what is right.")));

    private static QuizQuestion question(
            String id,
            String text,
            QuizOption... options) {
        return new QuizQuestion(id, text, List.of(options));
    }

    private static QuizOption option(
            String id,
            String text,
            CharacterId primary,
            CharacterId secondary) {
        return new QuizOption(id, text, primary, secondary);
    }
}