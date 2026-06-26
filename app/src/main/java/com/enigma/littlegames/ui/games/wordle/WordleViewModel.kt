package com.enigma.littlegames.ui.games.wordle

// ─────────────────────────────────────────────────────────────────────────────
// Wordle — ViewModel
// 6 attempts to guess a hidden 5-letter word.
// Green = correct position, Yellow = wrong position, Grey = absent.
// Daily mode uses date-seeded RNG; Free mode picks a new word each game.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*

enum class LetterResult { CORRECT, PRESENT, ABSENT }

enum class WordleMode(val label: String) {
    FREE ("Free Play"),
    DAILY("Daily Word"),
}

data class GuessRow(
    val letters: List<Char>            = emptyList(),
    val results: List<LetterResult>    = emptyList(),
    val isSubmitted: Boolean           = false,
)

data class WordleState(
    val target: String                      = "",
    val guesses: List<GuessRow>             = List(6) { GuessRow() },
    val currentRow: Int                     = 0,
    val currentInput: String               = "",
    val keyboardState: Map<Char, LetterResult> = emptyMap(),
    val isWon: Boolean                      = false,
    val isOver: Boolean                     = false,   // lost — all 6 used
    val mode: WordleMode                    = WordleMode.FREE,
    val hardMode: Boolean                   = false,
    val shake: Boolean                      = false,   // invalid word shake animation
    val reveal: Int                         = -1,      // row currently being revealed (-1 = none)
    val errorMessage: String               = "",
)

class WordleViewModel : ViewModel() {
    private val _state = MutableStateFlow(WordleState())
    val state: StateFlow<WordleState> = _state.asStateFlow()

    init { newGame(WordleMode.FREE, hardMode = false) }

    fun newGame(mode: WordleMode, hardMode: Boolean) {
        val target = when (mode) {
            WordleMode.DAILY -> dailyWord()
            WordleMode.FREE  -> WORD_LIST.random()
        }
        _state.value = WordleState(
            target    = target.uppercase(),
            mode      = mode,
            hardMode  = hardMode,
        )
    }

    fun onKey(ch: Char) {
        val s = _state.value
        if (s.isWon || s.isOver) return
        if (s.currentInput.length >= 5) return
        _state.update { it.copy(currentInput = it.currentInput + ch, errorMessage = "") }
    }

    fun onBackspace() {
        val s = _state.value
        if (s.currentInput.isEmpty()) return
        _state.update { it.copy(currentInput = it.currentInput.dropLast(1), errorMessage = "") }
    }

    fun onEnter() {
        val s = _state.value
        if (s.isWon || s.isOver) return
        if (s.currentInput.length < 5) {
            _state.update { it.copy(shake = true, errorMessage = "Not enough letters") }
            clearShake()
            return
        }

        val word = s.currentInput.uppercase()

        // Validate word in list
        if (word !in WORD_SET) {
            _state.update { it.copy(shake = true, errorMessage = "Not in word list") }
            clearShake()
            return
        }

        // Hard mode: must use known green/yellow letters
        if (s.hardMode) {
            val violation = hardModeViolation(s, word)
            if (violation != null) {
                _state.update { it.copy(shake = true, errorMessage = violation) }
                clearShake()
                return
            }
        }

        val results  = evaluateGuess(word, s.target)
        val newGuess = GuessRow(word.toList(), results, isSubmitted = true)
        val newGuesses = s.guesses.toMutableList()
        newGuesses[s.currentRow] = newGuess

        val won  = results.all { it == LetterResult.CORRECT }
        val over = !won && s.currentRow == 5

        // Update keyboard state
        val newKb = s.keyboardState.toMutableMap()
        word.forEachIndexed { i, ch ->
            val prev = newKb[ch]
            val next = results[i]
            // Never downgrade: CORRECT > PRESENT > ABSENT
            if (prev == null || next.ordinal < prev.ordinal) newKb[ch] = next
        }

        _state.update { it.copy(
            guesses       = newGuesses,
            currentRow    = if (won || over) s.currentRow else s.currentRow + 1,
            currentInput  = "",
            keyboardState = newKb,
            isWon         = won,
            isOver        = over,
            reveal        = s.currentRow,
        )}
    }

    fun clearShake() {
        _state.update { it.copy(shake = false) }
    }

    fun clearReveal() {
        _state.update { it.copy(reveal = -1) }
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    private fun evaluateGuess(guess: String, target: String): List<LetterResult> {
        val result = MutableList(5) { LetterResult.ABSENT }
        val targetCounts = target.groupingBy { it }.eachCount().toMutableMap()

        // Pass 1: mark correct positions
        for (i in 0..4) {
            if (guess[i] == target[i]) {
                result[i] = LetterResult.CORRECT
                targetCounts[guess[i]] = (targetCounts[guess[i]] ?: 1) - 1
            }
        }
        // Pass 2: mark present letters
        for (i in 0..4) {
            if (result[i] == LetterResult.CORRECT) continue
            val cnt = targetCounts[guess[i]] ?: 0
            if (cnt > 0) {
                result[i] = LetterResult.PRESENT
                targetCounts[guess[i]] = cnt - 1
            }
        }
        return result
    }

    private fun hardModeViolation(s: WordleState, word: String): String? {
        // Collect all green constraints from previous guesses
        for (row in s.guesses.filter { it.isSubmitted }) {
            row.letters.forEachIndexed { i, ch ->
                when (row.results[i]) {
                    LetterResult.CORRECT  ->
                        if (word[i] != ch) return "Position ${i+1} must be $ch"
                    LetterResult.PRESENT  ->
                        if (ch !in word) return "Must contain $ch"
                    else -> {}
                }
            }
        }
        return null
    }

    // ── Daily word ────────────────────────────────────────────────────────────

    private fun dailyWord(): String {
        val daysSinceEpoch = (System.currentTimeMillis() / 86_400_000L).toInt()
        return WORD_LIST[daysSinceEpoch % WORD_LIST.size]
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Word list — 300 common 5-letter words (subset; extend as desired)
// ─────────────────────────────────────────────────────────────────────────────

val WORD_LIST: List<String> = listOf(
    "about","above","abuse","actor","acute","admit","adopt","adult","after","again",
    "agent","agree","ahead","alarm","album","alert","alien","align","alike","alive",
    "alley","allow","alone","along","aloud","alter","angel","anger","angle","angry",
    "anime","ankle","annex","annoy","antic","anvil","aorta","apart","apple","apply",
    "apron","aptly","arena","argue","arise","armor","aroma","array","arrow","aside",
    "asset","atlas","attic","audio","audit","avoid","award","aware","awful","badly",
    "baker","basic","basis","batch","beach","began","begin","being","below","bench",
    "birds","birth","black","blade","blame","bland","blank","blast","blaze","bleed",
    "blend","bless","blind","block","blood","bloom","blown","board","bonus","boost",
    "booth","bound","brain","brand","brave","bread","break","breed","brick","bride",
    "brief","bring","broad","broke","brook","brown","brush","buddy","build","built",
    "bunch","burst","buyer","cabin","cable","cache","camel","candy","carry","catch",
    "cause","cedar","chain","chair","chaos","charm","chart","chase","cheap","check",
    "cheek","chess","chest","chief","child","china","choir","chunk","civic","civil",
    "claim","clamp","clash","class","clean","clear","clerk","click","cliff","climb",
    "cling","clock","clone","close","cloth","cloud","clown","coach","coast","cobra",
    "comet","comic","coral","could","count","court","cover","crack","craft","crane",
    "crash","crazy","cream","creek","crime","crisp","cross","crowd","crown","crush",
    "crust","cubic","curve","cycle","daily","dance","datum","death","debut","decoy",
    "delta","dense","depot","depth","derby","digit","dirty","disco","ditch","diver",
    "dizzy","doing","doubt","dough","draft","drain","drama","drank","drawl","drawn",
    "dream","dress","drift","drink","drive","drone","drove","dying","eager","early",
    "earth","eight","elite","email","ember","emote","empower","empty","enemy","enjoy",
    "enter","entry","equal","error","essay","event","every","exact","exist","extra",
    "fable","fabric","faint","faith","false","fancy","fatal","fault","feast","fence",
    "ferry","fetch","fever","fewer","fiber","field","fiend","fifth","fifty","fight",
    "final","first","fixed","flame","flash","flask","fleet","flesh","float","flood",
    "floor","flora","flour","fluid","flute","focus","forge","forth","forum","found",
    "frame","frank","fraud","fresh","front","frost","froze","fruit","fuels","fully",
    "funny","gauge","ghost","giant","given","gland","glass","glide","globe","gloom",
    "gloss","glove","going","grace","grade","grain","grand","grant","grape","grasp",
    "grass","grave","great","green","grief","grill","grind","groan","group","grove",
    "grown","guard","guess","guild","guilt","guise","gusto","habit","happy","harsh",
    "heart","heavy","hence","herbs","hills","hinge","hippo","holds","honor","horse",
    "hotel","hound","house","hover","human","hurry","hyper","ideal","image","imply",
    "inbox","index","indie","infer","inner","input","issue","ivory","jaunt","jewel",
    "joust","judge","juice","jumbo","keeps","kneel","knife","knock","known","label",
    "lance","large","laser","laugh","layer","leafy","learn","lease","ledge","legal",
    "lemon","level","light","linen","liver","local","lodge","logic","loose","lover",
    "lower","lucky","lunar","magic","major","maker","manor","maple","march","marsh",
    "match","mayor","media","mercy","merge","metal","might","minor","minus","model",
    "money","month","moral","motor","mount","mouse","mouth","moved","movie","muddy",
    "music","nadir","naive","nerve","never","newly","night","noble","noise","north",
    "noted","novel","nurse","nymph","occur","ocean","offer","often","olive","onion",
    "onset","optic","orbit","order","other","ought","outer","oxide","ozone","paint",
    "panel","panic","paper","paste","patch","pause","peace","peach","pearl","pedal",
    "penny","phone","photo","piano","picks","pilot","pinch","pixel","pizza","place",
    "plain","plane","plant","plate","plaza","plead","pluck","plumb","plume","plunge",
    "point","polar","posed","power","press","price","pride","prime","print","prize",
    "probe","prone","proof","prose","proud","prove","proxy","psalm","pulse","punch",
    "pupil","purse","queen","query","quest","queue","quick","quiet","quota","quite",
    "quota","quota","radar","radio","raise","rally","range","rapid","ratio","reach",
    "ready","realm","rebel","refer","reign","relax","reply","reuse","rider","ridge",
    "right","risky","rival","river","robin","rocky","rouge","rough","round","route",
    "royal","rugby","ruled","ruler","rural","sadly","saint","salad","sauce","scale",
    "scare","scene","scope","score","scout","seize","sense","serve","seven","shade",
    "shaft","shake","shall","shame","shape","share","shark","sharp","shift","shine",
    "shirt","shock","shoes","shore","short","shout","shove","sight","silly","since",
    "sixth","sixty","sized","skill","skimp","skull","slant","slate","sleek","sleep",
    "sleet","slide","slope","sloth","small","smart","smell","smile","smoke","snake",
    "solar","solid","solve","sorry","sound","south","space","spark","speak","speed",
    "spend","spice","spine","spite","split","spoke","spoon","sport","spray","squad",
    "stack","staff","stage","stain","stake","stand","stark","start","state","stays",
    "steam","steel","steep","stern","stick","stiff","still","stock","stone","stood",
    "store","storm","story","stove","strap","straw","stray","strip","stuck","study",
    "stuff","style","sugar","suite","super","surge","swamp","sweep","sweet","swept",
    "swift","swirl","sword","synth","taboo","taint","taken","talon","taunt","teach",
    "teeth","tempo","tense","tenth","thane","thank","theft","theme","there","these",
    "thick","thing","think","third","thorn","those","three","threw","throw","thumb",
    "tiger","tight","timer","tired","title","token","topic","torch","total","touch",
    "tough","tower","toxic","track","trade","trail","train","trait","tramp","trash",
    "tread","trend","trial","tribe","trick","tried","troop","trout","truce","truck",
    "truly","trust","truth","tumor","tuner","twist","ultra","uncle","under","undue",
    "union","unite","unity","until","upper","upset","urban","usage","usual","utter",
    "valve","vapor","vault","vigor","viola","viral","virus","visit","vista","vital",
    "vivid","vocal","voice","voter","wages","waltz","waste","watch","water","weary",
    "weave","wedge","weird","whale","wheat","wheel","where","which","while","white",
    "whole","whose","wider","wield","windy","witch","woman","women","world","worry",
    "worse","worst","worth","would","wound","wrath","wrist","wrote","yacht","yield",
    "young","yours","youth","zebra","zippy","zones","zooms",
).map { it.uppercase() }.filter { it.length == 5 }.distinct()

val WORD_SET: Set<String> = WORD_LIST.toSet()
