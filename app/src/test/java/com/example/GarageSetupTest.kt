package com.example

import com.example.data.api.GarageSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * The setup wizard decides three things that end up printed on paper and quoted down the phone:
 * the workshop's Client ID, its GST position, and how its invoices are numbered. These cover the
 * rules that would be expensive to get wrong — a duplicated invoice number, a Client ID that
 * changes shape, or a GSTIN accepted when it should not be.
 */
class GarageSetupTest {

    @Before
    fun resetInvoiceCounter() {
        GarageSettings.update(
            invoicePrefix = "SG",
            invoiceStartNumber = 1001,
            invoiceNextNumber = 0,
            syncToCloud = false
        )
    }

    // --- Garage code -------------------------------------------------------

    @Test
    fun `garage code takes the initials of a two word name`() {
        assertEquals("SG", GarageSettings.garageCodeFor("Shree Garage"))
        assertEquals("MA", GarageSettings.garageCodeFor("Mumbai Auto Works"))
    }

    @Test
    fun `a single word name falls back to its first two letters`() {
        assertEquals("SP", GarageSettings.garageCodeFor("Speedworks"))
    }

    @Test
    fun `a name with no usable letters still produces a well formed code`() {
        assertEquals("GG", GarageSettings.garageCodeFor(""))
        assertEquals("GG", GarageSettings.garageCodeFor("123 456"))
    }

    // --- Client ID ---------------------------------------------------------

    @Test
    fun `client id follows GG + code + YY + MM + serial`() {
        val august2026 = Calendar.getInstance().apply { set(2026, Calendar.AUGUST, 17) }.timeInMillis

        assertEquals(
            "GGSG-2608101",
            GarageSettings.formatClientId("Shree Garage", 101L, august2026)
        )
    }

    @Test
    fun `the serial is what increments between two garages registering together`() {
        val august2026 = Calendar.getInstance().apply { set(2026, Calendar.AUGUST, 17) }.timeInMillis

        val first = GarageSettings.formatClientId("Shree Garage", 101L, august2026)
        val second = GarageSettings.formatClientId("Krishna Motors", 102L, august2026)

        assertEquals("GGSG-2608101", first)
        assertEquals("GGKM-2608102", second)
    }

    // --- GSTIN -------------------------------------------------------------

    @Test
    fun `a well formed GSTIN is accepted`() {
        assertTrue(GarageSettings.isValidGstin("27ABCDE1234F1Z5"))
        assertTrue(GarageSettings.isValidGstin("  27abcde1234f1z5  "))
    }

    @Test
    fun `a malformed GSTIN is rejected`() {
        assertFalse(GarageSettings.isValidGstin(""))
        assertFalse(GarageSettings.isValidGstin("27ABCDE1234F1Z"))      // too short
        assertFalse(GarageSettings.isValidGstin("27ABCDE1234F1Y5"))     // 14th char must be Z
        assertFalse(GarageSettings.isValidGstin("2XABCDE1234F1Z5"))     // state code must be digits
    }

    // --- Invoice numbering -------------------------------------------------

    @Test
    fun `invoice numbers run on from the configured start`() {
        val issued = mutableListOf<String>()

        repeat(3) { issued += GarageSettings.allocateInvoiceNumber(issued) }

        assertEquals(listOf("SG1001", "SG1002", "SG1003"), issued)
    }

    @Test
    fun `a number already in use is never handed out twice`() {
        // A cloud restore can bring back job cards the local counter has not caught up with.
        val restored = listOf("SG1001", "SG1002", "SG1003")

        val next = GarageSettings.allocateInvoiceNumber(restored)

        assertEquals("SG1004", next)
        assertFalse(next in restored)
    }

    @Test
    fun `the counter never goes backwards when the start number is lowered`() {
        val issued = mutableListOf<String>()
        repeat(2) { issued += GarageSettings.allocateInvoiceNumber(issued) }   // SG1001, SG1002

        GarageSettings.update(invoiceStartNumber = 1, syncToCloud = false)
        val next = GarageSettings.allocateInvoiceNumber(issued)

        assertEquals("SG1003", next)
    }

    // --- Pending setup -----------------------------------------------------

    @Test
    fun `GSTIN is only pending while GST is applicable`() {
        GarageSettings.update(
            servicingTypeChosen = true,
            address = "Shop 4, MG Road, Pune",
            gstApplicable = true,
            gstin = "",
            invoicePrefix = "SG",
            invoiceStartNumber = 1001,
            syncToCloud = false
        )
        assertTrue("GSTIN" in GarageSettings.current.pendingSetup)

        GarageSettings.update(gstApplicable = false, syncToCloud = false)
        assertFalse("GSTIN" in GarageSettings.current.pendingSetup)
        assertTrue(GarageSettings.current.isFullyConfigured)
    }

    @Test
    fun `the servicing type is itself a required setup step`() {
        GarageSettings.update(
            servicingTypeChosen = false,
            address = "Shop 4, MG Road, Pune",
            gstApplicable = false,
            invoicePrefix = "SG",
            invoiceStartNumber = 1001,
            syncToCloud = false
        )

        assertTrue("Garage servicing type" in GarageSettings.current.pendingSetup)
        assertFalse(GarageSettings.current.isFullyConfigured)
    }

    @Test
    fun `an invalid GSTIN keeps the workshop unconfigured`() {
        GarageSettings.update(
            address = "Shop 4, MG Road, Pune",
            gstApplicable = true,
            gstin = "NOTAGSTIN",
            cgstPercent = 9.0,
            sgstPercent = 9.0,
            invoicePrefix = "SG",
            invoiceStartNumber = 1001,
            syncToCloud = false
        )

        assertTrue("Valid GSTIN" in GarageSettings.current.pendingSetup)
        assertFalse(GarageSettings.current.isFullyConfigured)
    }

    @Test
    fun `total GST is CGST plus SGST and disappears when GST is not applicable`() {
        GarageSettings.update(gstApplicable = true, cgstPercent = 9.0, sgstPercent = 9.0, syncToCloud = false)
        assertEquals(18.0, GarageSettings.current.gstRatePercent, 0.001)
        assertEquals(90.0, GarageSettings.current.cgstOn(1000.0), 0.001)
        assertEquals(90.0, GarageSettings.current.sgstOn(1000.0), 0.001)
        assertEquals(180.0, GarageSettings.current.taxOn(1000.0), 0.001)

        GarageSettings.update(gstApplicable = false, syncToCloud = false)
        assertEquals(0.0, GarageSettings.current.taxOn(1000.0), 0.001)
    }
}
