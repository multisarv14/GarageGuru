package com.example.data.catalogue

import com.example.data.api.GarageSettings.ServicingType

/**
 * Which kind of vehicle a catalogue entry — or a stock item — belongs to.
 *
 * [BOTH] is for the things that genuinely apply either way (a general wash, a battery terminal
 * clean), so a workshop that only does two-wheelers still sees them.
 */
enum class VehicleCategory(val stored: String, val label: String) {
    TWO_WHEELER("TWO_WHEELER", "2 Wheeler"),
    FOUR_WHEELER("FOUR_WHEELER", "4 Wheeler"),
    BOTH("BOTH", "Both");

    companion object {
        fun from(raw: String?): VehicleCategory =
            entries.firstOrNull { it.stored.equals(raw, ignoreCase = true) } ?: BOTH
    }
}

/** A priced entry in one of the master catalogues. */
data class CatalogueEntry(
    val title: String,
    val price: Double,
    val category: VehicleCategory
)

/**
 * The vehicle, service, spare and lubricant masters, each tagged with the vehicle category it
 * belongs to.
 *
 * The workshop's [ServicingType] is the single filter applied to all of them, so choosing
 * "2 Wheeler" in the setup wizard is what removes car makes from the job card, car services from
 * the invoice drawer and four-wheeler parts from the stock pickers — rather than each screen
 * carrying its own idea of what to show.
 */
object VehicleCatalogue {

    /** True when a workshop servicing [type] should be offered things in [category]. */
    fun matches(type: ServicingType, category: VehicleCategory): Boolean = when (category) {
        VehicleCategory.BOTH -> true
        VehicleCategory.TWO_WHEELER -> type.servicesTwoWheeler
        VehicleCategory.FOUR_WHEELER -> type.servicesFourWheeler
    }

    // ------------------------------------------------------------------
    // Vehicle master
    // ------------------------------------------------------------------

    /**
     * The vehicle master, as type → brand → model → variants.
     *
     * Nested rather than flat because the vehicle *type* is what narrows everything below it: a
     * scooter and a motorcycle share a brand but almost no models, and an electric scooter shares
     * neither. Keying on the type is what stops a job card offering an Activa under Motorcycle,
     * or a petrol model under Electric.
     */
    data class VehicleTypeSpec(
        val name: String,
        val category: VehicleCategory,
        val brands: Map<String, Map<String, List<String>>>
    )

    private val twoWheelerTypes = listOf(
        VehicleTypeSpec(
            "Motorcycle", VehicleCategory.TWO_WHEELER, mapOf(
                "Hero" to mapOf(
                    "Splendor Plus" to listOf("Standard", "Self Start", "i3S", "Black & Accent"),
                    "HF Deluxe" to listOf("Kick Start", "Self Start", "i3S"),
                    "Passion Pro" to listOf("Drum", "Disc"),
                    "Glamour" to listOf("Drum", "Disc", "XTEC"),
                    "Xtreme 125R" to listOf("Drum", "Disc", "Connected")
                ),
                "Honda" to mapOf(
                    "Shine" to listOf("Drum", "Disc", "SP 125"),
                    "SP 125" to listOf("Drum", "Disc"),
                    "Unicorn" to listOf("Standard"),
                    "Hornet 2.0" to listOf("Standard", "Repsol Edition")
                ),
                "Bajaj" to mapOf(
                    "Pulsar 125" to listOf("Drum", "Disc", "Carbon Fibre"),
                    "Pulsar 150" to listOf("Neon", "Twin Disc", "Single Disc"),
                    "Platina 100" to listOf("Drum", "ES", "Comfortec"),
                    "Avenger 220" to listOf("Cruise", "Street")
                ),
                "TVS" to mapOf(
                    "Apache RTR 160" to listOf("Drum", "Disc", "4V"),
                    "Raider 125" to listOf("Drum", "Disc", "SmartXonnect"),
                    "Radeon" to listOf("Drum", "Disc"),
                    "Sport" to listOf("Kick", "Electric Start")
                ),
                "Yamaha" to mapOf(
                    "FZ-S" to listOf("FI V3", "FI V4", "Deluxe"),
                    "R15" to listOf("V4", "V4 M", "S"),
                    "MT-15" to listOf("V2", "Standard"),
                    "Saluto" to listOf("Drum", "Disc")
                ),
                "Suzuki" to mapOf(
                    "Gixxer" to listOf("Standard", "SF", "SF 250"),
                    "Intruder" to listOf("Standard", "FI")
                ),
                "Royal Enfield" to mapOf(
                    "Classic 350" to listOf("Redditch", "Halcyon", "Signals", "Chrome", "Dark"),
                    "Bullet 350" to listOf("Standard", "Military", "Black Gold"),
                    "Hunter 350" to listOf("Retro", "Metro", "Rebel"),
                    "Himalayan" to listOf("Standard", "450"),
                    "Meteor 350" to listOf("Fireball", "Stellar", "Supernova")
                )
            )
        ),
        VehicleTypeSpec(
            "Scooter", VehicleCategory.TWO_WHEELER, mapOf(
                "Honda" to mapOf(
                    "Activa 6G" to listOf("STD", "DLX", "H-Smart"),
                    "Activa 125" to listOf("Drum", "Disc", "H-Smart"),
                    "Dio" to listOf("STD", "DLX", "H-Smart"),
                    "Grazia" to listOf("Drum", "Disc")
                ),
                "TVS" to mapOf(
                    "Jupiter" to listOf("Drum", "Disc", "ZX", "Classic"),
                    "Jupiter 125" to listOf("Drum", "Disc", "SmartXonnect"),
                    "Ntorq 125" to listOf("Drum", "Disc", "Race XP", "Super Squad"),
                    "Zest 110" to listOf("Standard", "Himalayan Highs")
                ),
                "Suzuki" to mapOf(
                    "Access 125" to listOf("Drum", "Disc", "Ride Connect"),
                    "Burgman Street" to listOf("Standard", "125 EX"),
                    "Avenis 125" to listOf("Standard", "Ride Connect")
                ),
                "Hero" to mapOf(
                    "Pleasure+" to listOf("Drum", "XTEC", "Platinum"),
                    "Maestro Edge 110" to listOf("Drum", "Disc"),
                    "Destini 125" to listOf("LX", "VX", "XTEC")
                ),
                "Yamaha" to mapOf(
                    "Fascino 125" to listOf("Drum", "Disc", "Hybrid"),
                    "RayZR 125" to listOf("Drum", "Disc", "Street Rally")
                ),
                "Aprilia" to mapOf(
                    "SR 160" to listOf("Standard", "Race"),
                    "SXR 160" to listOf("Standard")
                )
            )
        ),
        VehicleTypeSpec(
            "Electric Motorcycle", VehicleCategory.TWO_WHEELER, mapOf(
                "Revolt" to mapOf(
                    "RV400" to listOf("Standard", "BRZ"),
                    "RV1" to listOf("Standard", "RV1+")
                ),
                "Ultraviolette" to mapOf("F77" to listOf("Mach 2", "Recon", "Space Edition")),
                "Tork" to mapOf("Kratos" to listOf("R", "X")),
                "Oben" to mapOf("Rorr" to listOf("Standard", "EZ")),
                "Matter" to mapOf("Aera" to listOf("5000", "5000+"))
            )
        ),
        VehicleTypeSpec(
            "Electric Scooter", VehicleCategory.TWO_WHEELER, mapOf(
                "Ola Electric" to mapOf(
                    "S1 Pro" to listOf("Gen 2", "Gen 3"),
                    "S1 Air" to listOf("Standard"),
                    "S1 X" to listOf("2 kWh", "3 kWh", "4 kWh")
                ),
                "Ather" to mapOf(
                    "450X" to listOf("2.9 kWh", "3.7 kWh"),
                    "450S" to listOf("Standard"),
                    "Rizta" to listOf("S", "Z")
                ),
                "TVS" to mapOf("iQube" to listOf("Standard", "S", "ST")),
                "Bajaj" to mapOf("Chetak" to listOf("Urbane", "Premium", "3201")),
                "Hero Vida" to mapOf("V1" to listOf("Plus", "Pro")),
                "Ampere" to mapOf("Magnus" to listOf("EX", "Pro"), "Primus" to listOf("Standard")),
                "Okinawa" to mapOf("Praise" to listOf("Pro", "Standard"), "Ridge" to listOf("Plus", "Standard"))
            )
        ),
        VehicleTypeSpec(
            "Moped", VehicleCategory.TWO_WHEELER, mapOf(
                "TVS" to mapOf(
                    "XL100" to listOf("Standard", "Comfort", "Heavy Duty", "Win Edition")
                ),
                "Hero" to mapOf("Eco Deluxe" to listOf("Standard"))
            )
        )
    )

    private val fourWheelerTypes = listOf(
        VehicleTypeSpec(
            "Hatchback", VehicleCategory.FOUR_WHEELER, mapOf(
                "Maruti Suzuki" to mapOf(
                    "Swift" to listOf("LXI", "VXI", "ZXI", "ZXI+"),
                    "Baleno" to listOf("Sigma", "Delta", "Zeta", "Alpha"),
                    "WagonR" to listOf("LXI", "VXI", "ZXI"),
                    "Alto K10" to listOf("STD", "LXI", "VXI", "VXI+")
                ),
                "Hyundai" to mapOf(
                    "i20" to listOf("Era", "Magna", "Sportz", "Asta"),
                    "Grand i10 Nios" to listOf("Era", "Magna", "Sportz", "Asta")
                ),
                "Tata" to mapOf(
                    "Tiago" to listOf("XE", "XM", "XT", "XZ+"),
                    "Altroz" to listOf("XE", "XM", "XT", "XZ+")
                )
            )
        ),
        VehicleTypeSpec(
            "Sedan", VehicleCategory.FOUR_WHEELER, mapOf(
                "Honda" to mapOf(
                    "City" to listOf("SV", "V", "VX", "ZX"),
                    "Amaze" to listOf("E", "S", "VX")
                ),
                "Hyundai" to mapOf("Verna" to listOf("EX", "S", "SX", "SX(O)")),
                "Maruti Suzuki" to mapOf("Dzire" to listOf("LXI", "VXI", "ZXI", "ZXI+")),
                "Skoda" to mapOf("Slavia" to listOf("Active", "Ambition", "Style"))
            )
        ),
        VehicleTypeSpec(
            "SUV", VehicleCategory.FOUR_WHEELER, mapOf(
                "Tata" to mapOf(
                    "Nexon" to listOf("XE", "XM", "XT", "XZ+"),
                    "Punch" to listOf("Pure", "Adventure", "Accomplished"),
                    "Harrier" to listOf("XE", "XM", "XT", "XZ+"),
                    "Safari" to listOf("XE", "XM", "XT", "XZ+")
                ),
                "Hyundai" to mapOf(
                    "Creta" to listOf("E", "EX", "S", "SX", "SX(O)"),
                    "Venue" to listOf("E", "S", "SX", "SX(O)"),
                    "Exter" to listOf("EX", "S", "SX", "SX(O)")
                ),
                "Mahindra" to mapOf(
                    "Scorpio" to listOf("S", "S11", "N Z8"),
                    "Thar" to listOf("AX", "LX", "Roxx"),
                    "XUV700" to listOf("MX", "AX3", "AX5", "AX7"),
                    "Bolero" to listOf("B4", "B6", "B6(O)")
                ),
                "Kia" to mapOf(
                    "Seltos" to listOf("HTE", "HTK", "HTX", "GTX"),
                    "Sonet" to listOf("HTE", "HTK", "HTX", "GTX")
                ),
                "Maruti Suzuki" to mapOf("Brezza" to listOf("LXI", "VXI", "ZXI", "ZXI+"))
            )
        ),
        VehicleTypeSpec(
            "MUV / MPV", VehicleCategory.FOUR_WHEELER, mapOf(
                "Maruti Suzuki" to mapOf("Ertiga" to listOf("LXI", "VXI", "ZXI", "ZXI+")),
                "Toyota" to mapOf(
                    "Innova" to listOf("Crysta GX", "Crysta VX", "Hycross G", "Hycross ZX")
                ),
                "Kia" to mapOf("Carens" to listOf("Premium", "Prestige", "Luxury"))
            )
        ),
        VehicleTypeSpec(
            "Electric Car", VehicleCategory.FOUR_WHEELER, mapOf(
                "Tata" to mapOf(
                    "Nexon EV" to listOf("Creative", "Fearless", "Empowered"),
                    "Tiago EV" to listOf("XE", "XT", "XZ+")
                ),
                "MG" to mapOf("ZS EV" to listOf("Excite", "Exclusive"), "Comet EV" to listOf("Pace", "Play", "Plush")),
                "Mahindra" to mapOf("XUV400" to listOf("EC", "EL")),
                "Hyundai" to mapOf("Kona Electric" to listOf("Premium", "Premium Dual Tone"))
            )
        )
    )

    private val allVehicleTypes = twoWheelerTypes + fourWheelerTypes

    private fun specFor(vehicleType: String): VehicleTypeSpec? =
        allVehicleTypes.firstOrNull { it.name.equals(vehicleType, ignoreCase = true) }

    /** Vehicle types this workshop actually takes in. */
    fun vehicleTypes(type: ServicingType): List<String> =
        allVehicleTypes.filter { matches(type, it.category) }.map { it.name }

    /** The category a chosen vehicle type belongs to, used to narrow everything below it. */
    fun categoryOfVehicleType(vehicleType: String): VehicleCategory =
        specFor(vehicleType)?.category ?: VehicleCategory.BOTH

    /** Brands that actually build the chosen vehicle type. */
    fun brandsFor(vehicleType: String): List<String> =
        specFor(vehicleType)?.brands?.keys?.sorted().orEmpty()

    /** Models that brand builds *of that type* — an Activa never appears under Motorcycle. */
    fun modelsFor(vehicleType: String, brand: String): List<String> =
        specFor(vehicleType)?.brands?.get(brand)?.keys?.toList().orEmpty()

    fun variantsFor(vehicleType: String, brand: String, model: String): List<String> =
        specFor(vehicleType)?.brands?.get(brand)?.get(model).orEmpty()

    // ------------------------------------------------------------------
    // Service master
    // ------------------------------------------------------------------

    /**
     * The four-wheeler side of the service master.
     *
     * The entries that used to be marked BOTH now sit here. The 2-wheeler rate card below covers
     * all of them for a two-wheeler shop, so leaving them shared would have shown a bike garage
     * "Engine Oil Change" twice at two different prices.
     */
    private val fourWheelerServices = listOf(
        CatalogueEntry("General Inspection", 150.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Engine Oil Change", 300.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Oil Filter Replacement", 150.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Air Filter Cleaning", 80.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Air Filter Replacement", 200.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Battery Check & Terminal Clean", 120.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Brake Adjustment", 150.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Washing & Polishing", 250.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Wheel Alignment", 600.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Wheel Balancing", 400.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Tyre Rotation", 350.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("AC Service & Gas Top-up", 1800.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Coolant Flush", 900.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Brake Pad Replacement", 1200.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Suspension Check", 800.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Cabin Filter Replacement", 550.0, VehicleCategory.FOUR_WHEELER)
    )

    /** Two-wheeler rate card + four-wheeler services, as one master. */
    private val allServices = TwoWheelerServiceMaster.ENTRIES + fourWheelerServices

    /** The default services a workshop can import into its own Service Master. */
    fun defaultServicesFor(type: ServicingType): List<CatalogueEntry> =
        allServices.filter { matches(type, it.category) }

    fun services(type: ServicingType): List<CatalogueEntry> =
        allServices.filter { matches(type, it.category) }

    /**
     * Reduces a title to letters and digits so punctuation cannot decide a price.
     *
     * Bills written by earlier versions carry names like `Engine Oil (1L Fully Synthetic)` while
     * the catalogue now reads `Engine Oil 1L (Fully Synthetic)`. Stripping the brackets threw the
     * grade away and priced every oil as the cheapest one; comparing on the letters alone makes
     * the two forms identical.
     */
    private fun normalizeTitle(title: String): String =
        title.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Best catalogue match for a bill line, or null when nothing plausibly matches.
     *
     * Exact first, then the *longest* containment rather than the first — otherwise a short
     * generic entry wins over the specific one that was actually meant.
     */
    private fun lookupPrice(title: String, pool: List<CatalogueEntry>): Double? {
        val cleaned = title.replace(Regex("(?i)\\s-\\s*₹?\\s*[\\d.,]+\\s*$"), "")
        val needle = normalizeTitle(cleaned)
        if (needle.isEmpty()) return null

        pool.firstOrNull { normalizeTitle(it.title) == needle }?.let { return it.price }

        return pool
            .filter {
                val entry = normalizeTitle(it.title)
                entry.length > 3 && (needle.contains(entry) || entry.contains(needle))
            }
            .maxByOrNull { normalizeTitle(it.title).length }
            ?.price
    }

    /** Catalogue rate for a service, used when a bill line carries no price of its own. */
    fun servicePrice(title: String): Double? = lookupPrice(title, allServices)

    // ------------------------------------------------------------------
    // Spare parts master
    // ------------------------------------------------------------------

    private val allSpares = listOf(
        // Two wheeler
        CatalogueEntry("Clutch Cable", 150.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Accelerator Cable", 130.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Throttle Assembly", 400.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Speedometer", 1200.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Speedometer Gear", 300.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Odometer Cable", 150.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Digital Meter", 2000.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Chain Sprocket Kit", 1400.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Brake Shoe Set", 450.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Clutch Plate Set", 900.0, VehicleCategory.TWO_WHEELER),
        // Four wheeler
        CatalogueEntry("Brake Pad Set", 2200.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Brake Disc Rotor", 3500.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Cabin Air Filter", 650.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Wiper Blade Set", 800.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Radiator Hose", 1100.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Clutch Kit", 8500.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Shock Absorber", 3200.0, VehicleCategory.FOUR_WHEELER),
        // Either
        CatalogueEntry("Battery", 2500.0, VehicleCategory.BOTH),
        CatalogueEntry("Headlight Bulb", 250.0, VehicleCategory.BOTH),
        CatalogueEntry("Indicator Buzzer", 80.0, VehicleCategory.BOTH),
        CatalogueEntry("Flasher Relay", 120.0, VehicleCategory.BOTH),
        CatalogueEntry("Wiring Harness", 800.0, VehicleCategory.BOTH),
        CatalogueEntry("Horn", 350.0, VehicleCategory.BOTH)
    )

    fun spares(type: ServicingType): List<CatalogueEntry> =
        allSpares.filter { matches(type, it.category) }

    // ------------------------------------------------------------------
    // Oil & lubricants master
    // ------------------------------------------------------------------

    private val allLubes = listOf(
        CatalogueEntry("Engine Oil 1L (Mineral)", 350.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Engine Oil 1L (Semi Synthetic)", 600.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Engine Oil 1L (Fully Synthetic)", 900.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Fork Oil 500ml", 320.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Chain Lube Spray", 280.0, VehicleCategory.TWO_WHEELER),
        CatalogueEntry("Engine Oil 3.5L (Semi Synthetic)", 2100.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Engine Oil 4L (Fully Synthetic)", 3400.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Gear Oil 1L", 550.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Coolant 1L", 420.0, VehicleCategory.FOUR_WHEELER),
        CatalogueEntry("Brake Fluid DOT 4", 380.0, VehicleCategory.BOTH),
        CatalogueEntry("Grease 500g", 220.0, VehicleCategory.BOTH)
    )

    fun lubes(type: ServicingType): List<CatalogueEntry> =
        allLubes.filter { matches(type, it.category) }

    /** Catalogue rate for a part or lubricant, used when a bill line carries no price. */
    fun materialPrice(title: String): Double? = lookupPrice(title, allSpares + allLubes)

    /**
     * The category a stock item belongs to, guessed from its name when it was added before
     * categories existed. Existing rows keep working instead of vanishing from the pickers.
     */
    fun inferCategory(name: String): VehicleCategory {
        val lower = name.lowercase()
        val twoWheelerHints = listOf("chain", "sprocket", "fork", "clutch cable", "brake shoe", "speedometer", "carburett")
        val fourWheelerHints = listOf("cabin", "wiper", "radiator", "coolant", "shock absorber", "brake pad", "rotor", "3.5l", "4l")
        return when {
            fourWheelerHints.any { lower.contains(it) } -> VehicleCategory.FOUR_WHEELER
            twoWheelerHints.any { lower.contains(it) } -> VehicleCategory.TWO_WHEELER
            else -> VehicleCategory.BOTH
        }
    }
}
