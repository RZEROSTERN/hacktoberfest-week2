package mx.dev1.naturequest.domain.hunt

import java.text.Normalizer
import javax.inject.Inject

/**
 * Deterministic safety gate for generated hunt items. The prompt asks the model for safe items, but
 * a model cannot be trusted with safety: the spike produced "a coiled snake", "orange fungus" and "a
 * bird's nest hidden in a hollow". Every item must pass here before a child sees it.
 *
 * The rules mirror the product's safety rules: photograph, never pick; no touching or approaching
 * animals, insects or mushrooms; nothing near water or roads, off the path, or climbing; nothing to
 * eat or taste; no photos of people. It blocks by word (English and Spanish, accents ignored). It
 * errs on the side of rejecting: a rejected item is simply regenerated.
 */
class SafetyValidator @Inject constructor() {

    sealed interface Verdict {
        /** [text] is the cleaned item to show: trimmed, no quotes or final punctuation, capitalized. */
        data class Valid(val text: String) : Verdict
        data class Rejected(val reason: Reason, val detail: String? = null) : Verdict
    }

    enum class Reason { EMPTY, TOO_LONG, INVALID_CHARACTERS, UNSAFE, DUPLICATE }

    /** Checks [raw] and compares it with the already accepted [existing] items to avoid repeats. */
    fun validate(raw: String, existing: Collection<String> = emptyList()): Verdict {
        val cleaned = clean(raw)
        if (cleaned.isEmpty()) return Verdict.Rejected(Reason.EMPTY)
        if (cleaned.length > MAX_LENGTH || tokens(cleaned).size > MAX_WORDS) return Verdict.Rejected(Reason.TOO_LONG)
        if (cleaned.any { !it.isLetter() && it != ' ' && it != '-' && it != '\'' && it != ',' }) {
            return Verdict.Rejected(Reason.INVALID_CHARACTERS)
        }
        val normalized = normalize(cleaned)
        tokens(cleaned).firstOrNull { it in BLOCKED_WORDS }?.let { return Verdict.Rejected(Reason.UNSAFE, it) }
        BLOCKED_PHRASES.firstOrNull { normalized.contains(it) }?.let { return Verdict.Rejected(Reason.UNSAFE, it) }
        if (existing.any { normalize(it) == normalized }) return Verdict.Rejected(Reason.DUPLICATE)
        return Verdict.Valid(cleaned)
    }

    fun isSafe(raw: String): Boolean = validate(raw) is Verdict.Valid

    /**
     * True if a sentence the model wrote (feedback or a hint) could tell a child to pick, touch,
     * approach, eat or climb something, or to go near water or roads. Looser than [validate]:
     * sentences may describe the photo ("I see a bee"), and may say "take another photo".
     */
    fun containsUnsafeInstruction(text: String): Boolean =
        tokens(text).any { it in SENTENCE_BLOCKED_WORDS }

    private fun clean(raw: String): String =
        raw.trim()
            .trim('"', '\'', '`', '“', '”', '‘', '’', '.', '!', ';', ':', ' ')
            .replace(Regex("\\s+"), " ")
            .replaceFirstChar { it.uppercase() }

    /** Lowercase, accents removed, single spaces: the form used for matching and de-duplication. */
    private fun normalize(text: String): String =
        tokens(text).joinToString(" ")

    private fun tokens(text: String): List<String> {
        val folded = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        val result = mutableListOf<String>()
        val word = StringBuilder()
        for (c in folded) {
            if (c.isLetter()) {
                word.append(c)
            } else if (word.isNotEmpty()) {
                result += word.toString()
                word.clear()
            }
        }
        if (word.isNotEmpty()) result += word.toString()
        return result
    }

    private companion object {
        const val MAX_LENGTH = 60
        const val MAX_WORDS = 10

        private fun words(vararg groups: String): Set<String> =
            groups.flatMap { it.split(' ', '\n').filter(String::isNotBlank) }.toSet()

        val BLOCKED_PHRASES = listOf(
            "near water", "near the water", "in the water", "by the water", "next to water", "next to the water",
            "off the path", "leave the path", "off the trail", "turn over", "cerca del agua", "en el agua",
            "junto al agua", "fuera del camino", "dar la vuelta", "dale la vuelta",
        )

        /** For model-written sentences: harmful instructions and dangerous places only. */
        val SENTENCE_BLOCKED_WORDS: Set<String> = words(
            "pick picks picked picking pluck plucks plucked plucking collect collects collected collecting",
            "gather gathers gathered gathering touch touches touched touching grab grabs grabbed grabbing",
            "squeeze squeezes pet pets petting hug hugs kiss kisses lick licks smell smells smelling sniff",
            "catch catches caught catching chase chases chasing feed feeds feeding approach approaches",
            "approaching climb climbs climbed climbing swim swims swimming dig digs digging eat eats ate",
            "eaten eating taste tastes tasted tasting drink drinks bite chew",
            "lake lakes river rivers pond ponds stream streams creek creeks canal waterfall sea ocean cliff",
            "cliffs road roads street streets traffic highway berry berries mushroom mushrooms fungus fungi",
            "toadstool sharp glass needle syringe",
            "recoge recoger recogiendo arranca arrancar toca tocar tocando agarra agarrar acaricia acariciar",
            "abraza abrazar besa huele oler lame atrapa atrapar persigue perseguir alimenta alimentar acercate",
            "acercar trepa trepar escala escalar nadar cava cavar come comer comiendo",
            "lago lagos rio rios estanque arroyo cascada mar acantilado barranco calle calles carretera",
            "avenida trafico autopista baya bayas hongo hongos seta setas filoso vidrio aguja jeringa",
        )

        /** All entries lowercase without accents; matched against whole words only. */
        val BLOCKED_WORDS: Set<String> = words(
            // Touching, taking, approaching, eating (English)
            "pick picks picked picking pluck plucks plucked plucking collect collects collected collecting",
            "gather gathers gathered gathering take takes took taken taking grab grabs grabbed grabbing",
            "touch touches touched touching hold holds held holding feel feels felt feeling squeeze squeezes",
            "pet pets petting stroke strokes hug hugs kiss kisses lick licks smell smells smelling sniff",
            "catch catches caught catching chase chases chasing feed feeds feeding approach approaches approaching",
            "climb climbs climbed climbing jump jumps jumping swim swims swimming dig digs digging crawl",
            "cross crosses crossing eat eats ate eaten eating taste tastes tasted tasting drink drinks bite",
            "chew shake shakes pull pulls push pushes break breaks cut cuts lift lifts flip flips kick kicks",
            "throw throws step steps stomp",
            // Water, heights, roads, vehicles (English)
            "lake lakes river rivers pond ponds stream streams creek creeks canal waterfall sea ocean beach shore",
            "swamp fountain pool cliff cliffs ledge ravine canyon road roads street streets traffic highway",
            "intersection crosswalk car cars truck trucks bus buses motorcycle vehicle vehicles train trains",
            "railway railroad bridge roof rooftop ladder fire",
            // Animals, insects, fungi and hiding places (English)
            "snake snakes serpent spider spiders scorpion insect insects bug bugs bee bees wasp wasps hornet",
            "ant ants anthill beetle beetles caterpillar worm worms centipede millipede lizard gecko iguana",
            "frog toad bat rat rats rodent dog dogs cat cats animal animals creature creatures wildlife horse",
            "cow bull goat sheep pig mushroom mushrooms fungus fungi toadstool mold mould fly flies mosquito",
            "nest nests hive burrow hole holes hollow cave under beneath underneath inside hidden hiding",
            // Things to pick, eat or that can hurt (English)
            "berry berries fruit fruits thorn thorns thorny nettle poison poisonous toxic sharp glass needle",
            "syringe trash garbage litter broken",
            // People (privacy of children and strangers)
            "person people stranger strangers child children kid kids boy girl",

            // Touching, taking, approaching, eating (Spanish)
            "recoge recoger recogiendo recoja arranca arrancar arranque cortar toca tocar tocando agarra agarrar",
            "toma tomar coge coger lleva llevar sostiene sostener sosten atrapa atrapar persigue perseguir",
            "alimenta alimentar acaricia acariciar abraza abrazar besa huele oler olfatea lame acercate acercar",
            "acerca trepa trepar escala escalar salta saltar nada nadar cava cavar cruza cruzar come comer",
            "comiendo prueba probar bebe beber muerde mastica sacude jala jalar empuja empujar rompe romper",
            "levanta levantar voltea patea lanza pisa",
            // Water, heights, roads, vehicles (Spanish)
            "lago lagos rio rios estanque arroyo cascada mar playa orilla fuente pantano alberca acantilado",
            "barranco canon calle calles carretera avenida trafico autopista cruce coche coches carro carros",
            "auto autos camion autobus camioneta moto motocicleta vehiculo vehiculos tren puente techo azotea",
            "fuego",
            // Animals, insects, fungi and hiding places (Spanish)
            "serpiente serpientes culebra vibora arana aranas alacran escorpion insecto insectos bicho bichos",
            "abeja abejas avispa avispas avispon hormiga hormigas hormiguero escarabajo oruga gusano gusanos",
            "ciempies lagartija lagarto iguana rana sapo murcielago rata raton perro perros gato gatos animal",
            "animales criatura criaturas caballo vaca toro cabra oveja cerdo hongo hongos seta setas moho",
            "mosca moscas mosquito nido nidos colmena madriguera agujero hoyo hueco cueva debajo escondido",
            "escondida oculto oculta",
            // Things to pick, eat or that can hurt (Spanish)
            "baya bayas fruta frutas fruto frutos espina espinas espinoso ortiga veneno venenoso toxico filoso",
            "vidrio aguja jeringa basura",
            // People (Spanish)
            "persona personas gente extrano extranos nino nina ninos ninas chico chica",
        )
    }
}
