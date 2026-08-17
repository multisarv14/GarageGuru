package com.example

import com.example.data.api.GarageSettings
import com.example.data.model.JobCardEntity
import com.example.ui.screens.formatInvoiceItem
import com.example.ui.screens.parseInvoiceItems
import com.example.ui.screens.recalculatedJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The bill is stored as two comma-separated strings, so parsing them is what decides what a
 * customer is charged. These cover the cases that actually move money: an edited price surviving
 * a reload, a multi-quantity part not being re-priced, and bills written by earlier versions
 * still totalling to what they did before.
 */
class InvoiceItemParsingTest {

    private val emptyJob = JobCardEntity(
        customerName = "Test",
        customerPhone = "9999999999",
        vehicleName = "Bike: Honda Shine",
        vehiclePlate = "MH12AB1234",
        symptoms = ""
    )

    @Before
    fun resetSettings() {
        // No Android context in a plain unit test, so this only touches the in-memory state.
        GarageSettings.update(
            gstEnabled = true,
            gstRatePercent = 18.0,
            syncToCloud = false
        )
    }

    @Test
    fun `an edited line round-trips at its own price`() {
        val stored = formatInvoiceItem("Clutch Cable", 3, 175.0)
        val rows = parseInvoiceItems("", stored)

        assertEquals(1, rows.size)
        assertEquals("Clutch Cable", rows[0].description)
        assertEquals(3, rows[0].qty)
        assertEquals(175.0, rows[0].price, 0.001)
        assertEquals(525.0, rows[0].total, 0.001)
    }

    @Test
    fun `a service keeps a hand-entered price instead of the catalogue rate`() {
        // The catalogue says 300 for an oil change; the workshop quoted 450.
        val rows = parseInvoiceItems(formatInvoiceItem("Engine Oil Change", 1, 450.0), "")

        assertEquals(450.0, rows[0].price, 0.001)
    }

    @Test
    fun `a bare service name still falls back to the catalogue`() {
        val rows = parseInvoiceItems("Engine Oil Change", "")

        assertEquals("Engine Oil Change", rows[0].description)
        assertEquals(300.0, rows[0].price, 0.001)
    }

    @Test
    fun `legacy entries written with a line total keep their value`() {
        // Older versions wrote the whole line after the dash, not the unit price.
        val rows = parseInvoiceItems("", "Clutch Cable (2) - ₹300")

        assertEquals(2, rows[0].qty)
        assertEquals(150.0, rows[0].price, 0.001)
        assertEquals(300.0, rows[0].total, 0.001)
    }

    @Test
    fun `a hyphenated part name does not confuse the price marker`() {
        val rows = parseInvoiceItems("", formatInvoiceItem("Multi-point Check", 1, 400.0))

        assertEquals("Multi-point Check", rows[0].description)
        assertEquals(400.0, rows[0].price, 0.001)
    }

    @Test
    fun `duplicate lines are addressed by position, not by name`() {
        val materials = listOf(
            formatInvoiceItem("Brake Pad", 1, 500.0),
            formatInvoiceItem("Brake Pad", 2, 500.0)
        ).joinToString(",")

        val rows = parseInvoiceItems("", materials)

        assertEquals(2, rows.size)
        assertEquals(0, rows[0].sourceIndex)
        assertEquals(1, rows[1].sourceIndex)
        assertEquals(1, rows[0].qty)
        assertEquals(2, rows[1].qty)
    }

    @Test
    fun `totals add GST and then take the discount off the end`() {
        val job = recalculatedJob(
            emptyJob,
            tasks = formatInvoiceItem("Engine Oil Change", 1, 1000.0),
            materials = formatInvoiceItem("Oil Filter", 2, 250.0),
            discount = 100.0
        )

        assertEquals(1000.0, job.serviceCharges, 0.001)
        assertEquals(500.0, job.partsCharges, 0.001)
        assertEquals(270.0, job.taxes, 0.001)          // 18% of 1500
        assertEquals(100.0, job.discount, 0.001)
        assertEquals(1670.0, job.totalAmount, 0.001)   // 1500 + 270 - 100
    }

    @Test
    fun `no tax line is added when GST is switched off`() {
        GarageSettings.update(gstEnabled = false, syncToCloud = false)

        val job = recalculatedJob(emptyJob, tasks = formatInvoiceItem("Chain Cleaning", 1, 200.0))

        assertEquals(0.0, job.taxes, 0.001)
        assertEquals(200.0, job.totalAmount, 0.001)
    }

    @Test
    fun `a discount can zero a bill but never make it negative`() {
        val job = recalculatedJob(
            emptyJob,
            tasks = formatInvoiceItem("Chain Cleaning", 1, 100.0),
            discount = 5000.0
        )

        assertEquals(0.0, job.totalAmount, 0.001)
        assertTrue(job.discount <= 118.0)
    }
}
