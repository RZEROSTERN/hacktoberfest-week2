package mx.dev1.naturequest.domain.hunt

/** Awarded at the end of a hunt. Everyone who goes outside earns at least bronze. */
enum class Medal {
    BRONZE,
    SILVER,
    GOLD,
    ;

    companion object {
        /** Gold for finding everything, silver for at least half, bronze otherwise. */
        fun forResult(found: Int, total: Int): Medal = when {
            total > 0 && found >= total -> GOLD
            total > 0 && found * 2 >= total -> SILVER
            else -> BRONZE
        }
    }
}
