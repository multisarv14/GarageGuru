package com.example

import com.example.data.model.InventoryItemEntity
import com.example.data.model.StockUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Add Stock popup shows a different form per stock type, and the difference is not cosmetic:
 * a Service has no quantity, no cost price and no low-stock threshold, so it must never be
 * counted, never be "low", and never be refused by the invoice's stock check.
 */
class StockItemTest {

    private fun spare(purchase: Double, selling: Double, qty: Int = 10) = InventoryItemEntity(
        name = "Brake Pad Set",
        stockType = "Spare",
        companyName = "Bosch",
        partCode = "BP-1204",
        quantity = qty,
        unit = "Set",
        purchasePrice = purchase,
        minStockAlert = 3,
        unitPrice = selling
    )

    private fun service(charge: Double) = InventoryItemEntity(
        name = "Engine Oil Change",
        stockType = "Service",
        quantity = 0,
        minStockAlert = 0,
        unitPrice = charge
    )

    // --- Profit -------------------------------------------------------------

    @Test
    fun `profit is selling minus purchase — the spare example`() {
        // Purchase 500, Selling 700 -> Profit 200
        assertEquals(200.0, spare(purchase = 500.0, selling = 700.0).profitPerUnit, 0.001)
    }

    @Test
    fun `profit is selling minus purchase — the lube example`() {
        // Purchase 350, Selling 450 -> Profit 100
        val lube = InventoryItemEntity(
            name = "Engine Oil 1L",
            stockType = "Lube",
            quantity = 5,
            unit = "Bottle",
            purchasePrice = 350.0,
            minStockAlert = 2,
            unitPrice = 450.0
        )
        assertEquals(100.0, lube.profitPerUnit, 0.001)
    }

    @Test
    fun `selling below cost shows as a negative margin rather than zero`() {
        // Silently clamping to zero would hide a pricing mistake from the owner.
        assertEquals(-50.0, spare(purchase = 500.0, selling = 450.0).profitPerUnit, 0.001)
    }

    @Test
    fun `a service has no cost price so it reports no profit`() {
        assertEquals(0.0, service(charge = 300.0).profitPerUnit, 0.001)
    }

    // --- Service is not stock ----------------------------------------------

    @Test
    fun `a service does not track stock and a spare does`() {
        assertFalse(service(charge = 300.0).tracksStock)
        assertTrue(service(charge = 300.0).isService)
        assertTrue(spare(500.0, 700.0).tracksStock)
        assertFalse(spare(500.0, 700.0).isService)
    }

    @Test
    fun `stock type matching ignores case`() {
        assertTrue(service(300.0).copy(stockType = "service").isService)
        assertTrue(service(300.0).copy(stockType = "SERVICE").isService)
    }

    @Test
    fun `a service at zero quantity is not low stock`() {
        // A service sits at quantity 0 forever; treating that as "low" would flood the alert tab.
        val svc = service(charge = 300.0)
        val isLow = svc.tracksStock && svc.quantity <= svc.minStockAlert
        assertFalse(isLow)
    }

    @Test
    fun `a spare at or below its threshold is low stock`() {
        val low = spare(500.0, 700.0, qty = 3)
        assertTrue(low.tracksStock && low.quantity <= low.minStockAlert)

        val fine = spare(500.0, 700.0, qty = 4)
        assertFalse(fine.tracksStock && fine.quantity <= fine.minStockAlert)
    }

    // --- Units --------------------------------------------------------------

    @Test
    fun `spare units are the countable ones`() {
        assertEquals(
            listOf("Piece", "Pair", "Set", "Kit", "Box", "Pack", "Meter", "Kg", "Litre", "Other"),
            StockUnits.forStockType("Spare")
        )
    }

    @Test
    fun `lube units are the measured and container ones`() {
        assertEquals(
            listOf("ml", "Litre", "Bottle", "Can", "Tube", "Pack", "Box", "Spray Can", "NOS", "Other"),
            StockUnits.forStockType("Lube")
        )
    }

    @Test
    fun `a service is offered no units at all`() {
        assertTrue(StockUnits.forStockType("Service").isEmpty())
        assertEquals("", StockUnits.defaultFor("Service"))
    }

    @Test
    fun `each type defaults to the first unit in its own list`() {
        assertEquals("Piece", StockUnits.defaultFor("Spare"))
        assertEquals("ml", StockUnits.defaultFor("Lube"))
    }

    @Test
    fun `unit lookup ignores case`() {
        assertEquals(StockUnits.LUBE, StockUnits.forStockType("lube"))
        assertEquals(StockUnits.SPARE, StockUnits.forStockType("SPARE"))
    }
}
