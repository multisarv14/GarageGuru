package com.example.data.model

/**
 * The units a quantity can be counted in, which depend on what is being counted.
 *
 * A spare is counted in pieces, pairs or sets; a lubricant in millilitres, bottles or cans.
 * Offering one combined list would mean asking a storeman to find "Spray Can" among wheel-nut
 * units, so the two are kept apart and chosen by stock type.
 */
object StockUnits {

    /** Countable goods: fasteners, cables, filters, brake pads. */
    val SPARE = listOf(
        "Piece", "Pair", "Set", "Kit", "Box", "Pack", "Meter", "Kg", "Litre", "Other"
    )

    /** Oils, greases and sprays, which are measured or come in their own containers. */
    val LUBE = listOf(
        "ml", "Litre", "Bottle", "Can", "Tube", "Pack", "Box", "Spray Can", "NOS", "Other"
    )

    /** The right list for a stock type. A Service has no quantity, so it has no units either. */
    fun forStockType(stockType: String): List<String> = when {
        stockType.equals("Lube", ignoreCase = true) -> LUBE
        stockType.equals("Spare", ignoreCase = true) -> SPARE
        else -> emptyList()
    }

    /** What a newly chosen stock type should default to. */
    fun defaultFor(stockType: String): String = forStockType(stockType).firstOrNull().orEmpty()
}
