package com.example

import com.example.data.api.GarageSettings.ServicingType
import com.example.data.catalogue.VehicleCatalogue
import com.example.data.catalogue.VehicleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The servicing type chosen in the setup wizard is meant to reach every master in the app. These
 * check the filter itself rather than each screen: if a two-wheeler workshop can still see car
 * makes or 4L engine oil here, it will see them on the job card and in the invoice drawer too.
 */
class ServicingTypeTest {

    // --- Vehicle master ----------------------------------------------------

    @Test
    fun `a two wheeler workshop is offered only two wheeler vehicle types`() {
        val types = VehicleCatalogue.vehicleTypes(ServicingType.TWO_WHEELER)

        assertEquals(
            listOf("Motorcycle", "Scooter", "Electric Motorcycle", "Electric Scooter", "Moped"),
            types
        )
    }

    @Test
    fun `a four wheeler workshop is offered no two wheeler types`() {
        val types = VehicleCatalogue.vehicleTypes(ServicingType.FOUR_WHEELER)

        assertFalse(types.contains("Motorcycle"))
        assertFalse(types.contains("Scooter"))
        assertTrue(types.contains("SUV"))
    }

    @Test
    fun `a workshop doing both sees every vehicle type`() {
        val types = VehicleCatalogue.vehicleTypes(ServicingType.BOTH)

        assertTrue(types.containsAll(listOf("Motorcycle", "Scooter", "Moped", "SUV", "Sedan")))
    }

    @Test
    fun `the vehicle type narrows the brands`() {
        // Aprilia builds scooters here but no motorcycles; Royal Enfield the reverse.
        assertTrue(VehicleCatalogue.brandsFor("Scooter").contains("Aprilia"))
        assertFalse(VehicleCatalogue.brandsFor("Motorcycle").contains("Aprilia"))
        assertTrue(VehicleCatalogue.brandsFor("Motorcycle").contains("Royal Enfield"))
        assertFalse(VehicleCatalogue.brandsFor("Scooter").contains("Royal Enfield"))
    }

    @Test
    fun `a brand that builds both never mixes its models across types`() {
        // Honda's Activa is a scooter and its Shine a motorcycle — neither may cross over.
        val hondaScooters = VehicleCatalogue.modelsFor("Scooter", "Honda")
        val hondaBikes = VehicleCatalogue.modelsFor("Motorcycle", "Honda")

        assertTrue(hondaScooters.contains("Activa 6G"))
        assertFalse(hondaScooters.contains("Shine"))
        assertTrue(hondaBikes.contains("Shine"))
        assertFalse(hondaBikes.contains("Activa 6G"))
    }

    @Test
    fun `the worked example resolves end to end`() {
        // Scooter -> Honda -> Activa 6G -> H-Smart
        assertTrue(VehicleCatalogue.vehicleTypes(ServicingType.TWO_WHEELER).contains("Scooter"))
        assertTrue(VehicleCatalogue.brandsFor("Scooter").contains("Honda"))
        assertTrue(VehicleCatalogue.modelsFor("Scooter", "Honda").contains("Activa 6G"))
        assertTrue(VehicleCatalogue.variantsFor("Scooter", "Honda", "Activa 6G").contains("H-Smart"))
    }

    @Test
    fun `electric types offer only electric brands`() {
        val eScooterBrands = VehicleCatalogue.brandsFor("Electric Scooter")

        assertTrue(eScooterBrands.containsAll(listOf("Ather", "Ola Electric")))
        assertFalse(eScooterBrands.contains("Aprilia"))
        assertFalse(VehicleCatalogue.brandsFor("Electric Motorcycle").contains("Hero"))
    }

    @Test
    fun `an unknown type or brand yields nothing rather than a wrong list`() {
        assertTrue(VehicleCatalogue.brandsFor("Spaceship").isEmpty())
        assertTrue(VehicleCatalogue.modelsFor("Scooter", "Ferrari").isEmpty())
        assertTrue(VehicleCatalogue.variantsFor("Scooter", "Honda", "Nonexistent").isEmpty())
        assertTrue(VehicleCatalogue.brandsFor("").isEmpty())
    }

    // --- Service, spare and lubricant masters ------------------------------

    @Test
    fun `two wheeler services exclude four wheeler labour but keep the shared ones`() {
        val titles = VehicleCatalogue.services(ServicingType.TWO_WHEELER).map { it.title }

        assertTrue(titles.contains("Chain Lubrication"))
        assertTrue(titles.contains("Engine Oil Change"))
        assertFalse(titles.contains("Wheel Alignment"))
        assertFalse(titles.contains("AC Service & Gas Top-up"))
    }

    @Test
    fun `four wheeler spares exclude two wheeler parts but keep the shared ones`() {
        val titles = VehicleCatalogue.spares(ServicingType.FOUR_WHEELER).map { it.title }

        assertTrue(titles.contains("Brake Disc Rotor"))
        assertTrue(titles.contains("Battery"))            // applies either way
        assertFalse(titles.contains("Chain Sprocket Kit"))
    }

    @Test
    fun `lubricants follow the same rule`() {
        val twoWheeler = VehicleCatalogue.lubes(ServicingType.TWO_WHEELER).map { it.title }
        val fourWheeler = VehicleCatalogue.lubes(ServicingType.FOUR_WHEELER).map { it.title }

        assertTrue(twoWheeler.any { it.contains("Fork Oil") })
        assertFalse(twoWheeler.any { it.contains("Coolant") })
        assertTrue(fourWheeler.any { it.contains("Coolant") })
        assertFalse(fourWheeler.any { it.contains("Fork Oil") })
        // Shared items appear on both sides.
        assertTrue(twoWheeler.contains("Brake Fluid DOT 4"))
        assertTrue(fourWheeler.contains("Brake Fluid DOT 4"))
    }

    @Test
    fun `a workshop doing both sees every master entry`() {
        assertEquals(
            VehicleCatalogue.services(ServicingType.TWO_WHEELER).size +
                VehicleCatalogue.services(ServicingType.FOUR_WHEELER).size -
                VehicleCatalogue.services(ServicingType.BOTH)
                    .count { it.category == VehicleCategory.BOTH },
            VehicleCatalogue.services(ServicingType.BOTH).size
        )
    }

    // --- Stock filtering ---------------------------------------------------

    @Test
    fun `stock recorded before categories existed stays visible to everyone`() {
        // Anything migrated carries BOTH, so it must never drop out of a picker.
        assertTrue(VehicleCatalogue.matches(ServicingType.TWO_WHEELER, VehicleCategory.BOTH))
        assertTrue(VehicleCatalogue.matches(ServicingType.FOUR_WHEELER, VehicleCategory.BOTH))
    }

    @Test
    fun `stock is hidden from a workshop that cannot use it`() {
        assertFalse(VehicleCatalogue.matches(ServicingType.TWO_WHEELER, VehicleCategory.FOUR_WHEELER))
        assertFalse(VehicleCatalogue.matches(ServicingType.FOUR_WHEELER, VehicleCategory.TWO_WHEELER))
        assertTrue(VehicleCatalogue.matches(ServicingType.BOTH, VehicleCategory.FOUR_WHEELER))
    }

    @Test
    fun `an unknown stored category degrades to BOTH rather than vanishing`() {
        assertEquals(VehicleCategory.BOTH, VehicleCategory.from(null))
        assertEquals(VehicleCategory.BOTH, VehicleCategory.from("nonsense"))
        assertEquals(VehicleCategory.TWO_WHEELER, VehicleCategory.from("TWO_WHEELER"))
    }

    @Test
    fun `an unknown stored servicing type degrades to BOTH`() {
        assertEquals(ServicingType.BOTH, ServicingType.from(null))
        assertEquals(ServicingType.BOTH, ServicingType.from(""))
        assertEquals(ServicingType.TWO_WHEELER, ServicingType.from("TWO_WHEELER"))
    }

    // --- The imported 2-wheeler rate card ----------------------------------

    @Test
    fun `the two wheeler rate card is the full imported list`() {
        val services = VehicleCatalogue.services(ServicingType.TWO_WHEELER)

        assertEquals(199, services.size)
        assertTrue(services.all { it.category == VehicleCategory.TWO_WHEELER })
    }

    @Test
    fun `rate card prices come through exactly as supplied`() {
        assertEquals(399.0, VehicleCatalogue.servicePrice("General Service")!!, 0.001)
        assertEquals(499.0, VehicleCatalogue.servicePrice("Basic Service")!!, 0.001)
        assertEquals(799.0, VehicleCatalogue.servicePrice("Full Service")!!, 0.001)
        assertEquals(150.0, VehicleCatalogue.servicePrice("Engine Oil Change")!!, 0.001)
    }

    @Test
    fun `no rate card name can break the bill line format`() {
        // A comma, " - " or " @ " in a name would be read back as a separator or a price.
        VehicleCatalogue.defaultServicesFor(ServicingType.TWO_WHEELER).forEach {
            assertFalse("comma in '${it.title}'", it.title.contains(","))
            assertFalse("ascii hyphen marker in '${it.title}'", it.title.contains(" - "))
            assertFalse("at marker in '${it.title}'", it.title.contains(" @ "))
        }
    }

    @Test
    fun `the rate card carries no duplicate names`() {
        val names = VehicleCatalogue.defaultServicesFor(ServicingType.TWO_WHEELER).map { it.title }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `a two wheeler shop is not shown a service twice at two prices`() {
        // The old shared entries moved to four-wheeler precisely to avoid this.
        val names = VehicleCatalogue.services(ServicingType.TWO_WHEELER).map { it.title }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `a four wheeler shop keeps the services it had`() {
        val titles = VehicleCatalogue.services(ServicingType.FOUR_WHEELER).map { it.title }

        assertTrue(titles.contains("Wheel Alignment"))
        assertTrue(titles.contains("Engine Oil Change"))
        assertTrue(titles.contains("AC Service & Gas Top-up"))
        assertFalse(titles.contains("Chain Lubrication"))
    }

    // --- Catalogue-backed pricing -----------------------------------------

    @Test
    fun `prices come from the master catalogue for both services and materials`() {
        // Engine Oil Change resolves to the 2-wheeler rate card entry.
        assertEquals(150.0, VehicleCatalogue.servicePrice("Engine Oil Change")!!, 0.001)
        assertEquals(600.0, VehicleCatalogue.servicePrice("Wheel Alignment")!!, 0.001)
        assertEquals(150.0, VehicleCatalogue.materialPrice("Clutch Cable")!!, 0.001)
    }

    @Test
    fun `oil grades written by earlier versions keep their own price`() {
        // The catalogue reads "Engine Oil 1L (Mineral)"; older bills wrote
        // "Engine Oil (1L Mineral)". Matching on punctuation collapsed all three grades onto
        // the cheapest one, so a fully synthetic oil re-priced from 900 to 350.
        assertEquals(350.0, VehicleCatalogue.materialPrice("Engine Oil (1L Mineral)")!!, 0.001)
        assertEquals(600.0, VehicleCatalogue.materialPrice("Engine Oil (1L Semi Synthetic)")!!, 0.001)
        assertEquals(900.0, VehicleCatalogue.materialPrice("Engine Oil (1L Fully Synthetic)")!!, 0.001)
    }

    @Test
    fun `a trailing price on the stored name does not confuse the lookup`() {
        assertEquals(150.0, VehicleCatalogue.materialPrice("Clutch Cable - ₹150")!!, 0.001)
    }

    @Test
    fun `the most specific catalogue entry wins over a shorter one`() {
        // "Brake Pad Set" must not be answered by the shorter, unrelated "Battery".
        assertEquals(2200.0, VehicleCatalogue.materialPrice("Brake Pad Set")!!, 0.001)
    }

    @Test
    fun `an unknown item has no catalogue price rather than a wrong one`() {
        assertEquals(null, VehicleCatalogue.materialPrice("Custom Fabricated Bracket"))
        assertEquals(null, VehicleCatalogue.servicePrice("Bespoke Dyno Tuning"))
    }
}
