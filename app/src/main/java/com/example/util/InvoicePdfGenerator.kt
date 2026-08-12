package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.JobCardEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfInvoiceItem(
    val index: Int,
    val name: String,
    val qty: Int,
    val unitPrice: Double,
    val totalAmount: Double
)

object InvoicePdfGenerator {

    private const val TAG = "InvoicePdfGenerator"

    /** Workshops quote a three-month interval, so the invoice carries the next due date. */
    fun nextServiceDueDate(fromMillis: Long): String {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = fromMillis
            add(java.util.Calendar.MONTH, 3)
        }
        return java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(cal.time)
    }

    fun generatePdfFile(context: Context, job: JobCardEntity): File? {
        try {
            val pdfDocument = PdfDocument()
            // Standard A4 dimensions: 595 x 842 points
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Paints
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#1E293B")
            }

            val primaryPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#5B4DFF")
            }

            val bgPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#F8FAFC")
            }

            val borderPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            // 1. Top Decorative Primary Color Accent Line
            primaryPaint.style = Paint.Style.FILL
            canvas.drawRect(36f, 36f, 559f, 42f, primaryPaint)

            // 2. Workshop Brand Header
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 20f
            textPaint.color = Color.parseColor("#0F172A")
            // Header comes from the signed-in workshop, not a hardcoded sample garage.
            val session = com.example.data.api.GarageSession

            // Garage logo, decoded from the Firestore Blob the app already caches.
            var textLeft = 36f
            com.example.data.api.GarageLogoStore.logo.value?.let { bytes ->
                if (bytes.isNotEmpty()) {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { logo ->
                        val box = 44f
                        canvas.drawBitmap(
                            logo,
                            null,
                            android.graphics.RectF(36f, 52f, 36f + box, 52f + box),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                        )
                        textLeft = 36f + box + 10f
                    }
                }
            }

            canvas.drawText(session.garageName.ifBlank { "Garage Guru" }.uppercase(), textLeft, 68f, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = 9f
            textPaint.color = Color.parseColor("#64748B")
            canvas.drawText("Automotive Repair, Servicing & Spare Parts Center", textLeft, 82f, textPaint)
            if (session.ownerPhone.isNotBlank()) {
                canvas.drawText("Tel: ${session.ownerPhone}", textLeft, 94f, textPaint)
            }

            // Right side Header: TAX INVOICE
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 16f
            textPaint.color = Color.parseColor("#5B4DFF")
            val invoiceHeaderTitle = if (job.status.equals("ESTIMATE", ignoreCase = true)) "ESTIMATE QUOTE" else "TAX INVOICE"
            canvas.drawText(invoiceHeaderTitle, 400f, 68f, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 10f
            textPaint.color = Color.parseColor("#1E293B")
            canvas.drawText("Invoice #: GG-INV-${job.id}", 400f, 84f, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = 9f
            textPaint.color = Color.parseColor("#64748B")
            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(job.creationTime))
            canvas.drawText("Date: $dateStr", 400f, 96f, textPaint)

            // 3. Customer & Vehicle Box
            val boxTop = 110f
            val boxBottom = 175f
            canvas.drawRect(36f, boxTop, 559f, boxBottom, bgPaint)
            canvas.drawRect(36f, boxTop, 559f, boxBottom, borderPaint)

            // Left Box: Customer Info
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 9f
            textPaint.color = Color.parseColor("#5B4DFF")
            canvas.drawText("BILL TO (CUSTOMER):", 48f, boxTop + 18f, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 11f
            textPaint.color = Color.parseColor("#0F172A")
            canvas.drawText(job.customerName.ifBlank { "Walk-in Customer" }, 48f, boxTop + 34f, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = 9f
            textPaint.color = Color.parseColor("#475569")
            canvas.drawText("Phone: ${job.customerPhone.ifBlank { "N/A" }}", 48f, boxTop + 48f, textPaint)

            // Divider vertical line in Box
            canvas.drawLine(300f, boxTop + 10f, 300f, boxBottom - 10f, borderPaint)

            // Right Box: Vehicle Info
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 9f
            textPaint.color = Color.parseColor("#5B4DFF")
            canvas.drawText("VEHICLE DETAILS:", 312f, boxTop + 18f, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 11f
            textPaint.color = Color.parseColor("#0F172A")
            canvas.drawText(job.vehicleName.ifBlank { "Two Wheeler" }, 312f, boxTop + 34f, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = 9f
            textPaint.color = Color.parseColor("#475569")
            canvas.drawText("Reg Plate: ${job.vehiclePlate.ifBlank { "N/A" }}", 312f, boxTop + 48f, textPaint)

            // Status Badge
            val statusColor = when (job.status.uppercase()) {
                "PAID", "CLOSED" -> Color.parseColor("#10B981")
                "ESTIMATE" -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#3B82F6")
            }
            textPaint.color = statusColor
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 10f
            canvas.drawText("[ ${job.status.uppercase()} ]", 480f, boxTop + 18f, textPaint)

            // 4. Itemized Service & Parts Table
            var currentY = 195f
            val tableHeaderHeight = 22f

            // Table Header Background
            primaryPaint.color = Color.parseColor("#5B4DFF")
            canvas.drawRect(36f, currentY, 559f, currentY + tableHeaderHeight, primaryPaint)

            // Table Header Text
            textPaint.color = Color.WHITE
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 9f

            canvas.drawText("#", 45f, currentY + 14f, textPaint)
            canvas.drawText("PART / SERVICE DESCRIPTION", 75f, currentY + 14f, textPaint)
            canvas.drawText("QTY", 360f, currentY + 14f, textPaint)
            canvas.drawText("UNIT PRICE", 420f, currentY + 14f, textPaint)
            canvas.drawText("TOTAL (₹)", 495f, currentY + 14f, textPaint)

            currentY += tableHeaderHeight

            // Parse Invoice Row Items
            val items = parseJobItems(job.tasks, job.materials)

            textPaint.color = Color.parseColor("#1E293B")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = 9f

            items.forEachIndexed { index, item ->
                val rowHeight = 20f
                if (index % 2 == 1) {
                    canvas.drawRect(36f, currentY, 559f, currentY + rowHeight, bgPaint)
                }

                canvas.drawText("${item.index}", 45f, currentY + 14f, textPaint)
                val truncatedName = if (item.name.length > 42) item.name.substring(0, 40) + ".." else item.name
                canvas.drawText(truncatedName, 75f, currentY + 14f, textPaint)
                canvas.drawText("${item.qty}", 365f, currentY + 14f, textPaint)
                canvas.drawText("₹${String.format("%.0f", item.unitPrice)}", 420f, currentY + 14f, textPaint)
                canvas.drawText("₹${String.format("%.0f", item.totalAmount)}", 495f, currentY + 14f, textPaint)

                canvas.drawLine(36f, currentY + rowHeight, 559f, currentY + rowHeight, borderPaint)
                currentY += rowHeight
            }

            // Table outer border
            canvas.drawLine(36f, 195f, 36f, currentY, borderPaint)
            canvas.drawLine(559f, 195f, 559f, currentY, borderPaint)

            currentY += 15f

            // 5. Financial Summary Block
            val summaryLeft = 320f
            val summaryRight = 559f

            textPaint.textSize = 9f
            textPaint.color = Color.parseColor("#475569")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            canvas.drawText("Service Labor Charges:", summaryLeft, currentY, textPaint)
            canvas.drawText("₹${String.format("%.0f", job.serviceCharges)}", 495f, currentY, textPaint)
            currentY += 14f

            canvas.drawText("Spare Parts & Materials:", summaryLeft, currentY, textPaint)
            canvas.drawText("₹${String.format("%.0f", job.partsCharges)}", 495f, currentY, textPaint)
            currentY += 14f

            if (job.taxes > 0) {
                canvas.drawText("Taxes / GST:", summaryLeft, currentY, textPaint)
                canvas.drawText("₹${String.format("%.0f", job.taxes)}", 495f, currentY, textPaint)
                currentY += 14f
            }

            // Total Payable Highlight Box
            currentY += 4f
            primaryPaint.color = Color.parseColor("#EEF2FF")
            canvas.drawRect(summaryLeft - 10f, currentY, summaryRight, currentY + 26f, primaryPaint)

            borderPaint.color = Color.parseColor("#5B4DFF")
            borderPaint.strokeWidth = 1.5f
            canvas.drawRect(summaryLeft - 10f, currentY, summaryRight, currentY + 26f, borderPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 12f
            textPaint.color = Color.parseColor("#5B4DFF")
            canvas.drawText("TOTAL AMOUNT:", summaryLeft, currentY + 17f, textPaint)
            canvas.drawText("₹${String.format("%.0f", job.totalAmount)}", 480f, currentY + 17f, textPaint)

            // 6. Terms & Signatures (Fixed near bottom)
            val footerY = 730f

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 8f
            textPaint.color = Color.parseColor("#64748B")
            canvas.drawText("TERMS & CONDITIONS:", 36f, footerY, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = 7.5f
            textPaint.color = Color.parseColor("#94A3B8")
            canvas.drawText("1. Goods once sold or fitted will not be returned/exchanged.", 36f, footerY + 12f, textPaint)
            canvas.drawText("2. Warranty on spare parts as per OEM manufacturer terms only.", 36f, footerY + 22f, textPaint)
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.color = Color.parseColor("#5B4DFF")
            canvas.drawText(
                "3. Next service due on ${nextServiceDueDate(job.creationTime)} (3 months from this service).",
                36f, footerY + 32f, textPaint
            )
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.color = Color.parseColor("#94A3B8")

            // Signature Lines
            borderPaint.color = Color.parseColor("#CBD5E1")
            borderPaint.strokeWidth = 1f
            canvas.drawLine(36f, footerY + 65f, 160f, footerY + 65f, borderPaint)
            canvas.drawText("Customer Signature", 50f, footerY + 76f, textPaint)

            canvas.drawLine(435f, footerY + 65f, 559f, footerY + 65f, borderPaint)
            canvas.drawText("Authorized Signatory", 445f, footerY + 76f, textPaint)

            pdfDocument.finishPage(page)

            // Write PDF to storage
            val fileName = "Invoice_GG_${job.id}_${job.vehiclePlate.replace(" ", "_")}.pdf"
            val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            if (!documentsDir.exists()) {
                documentsDir.mkdirs()
            }
            val pdfFile = File(documentsDir, fileName)

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            Log.d(TAG, "Generated PDF Invoice successfully: ${pdfFile.absolutePath}")
            return pdfFile
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF invoice", e)
            return null
        }
    }

    private fun parseJobItems(tasks: String, materials: String): List<PdfInvoiceItem> {
        val result = mutableListOf<PdfInvoiceItem>()
        var index = 1

        if (tasks.isNotBlank()) {
            tasks.split(",").forEach { task ->
                val trimmed = task.trim()
                if (trimmed.isNotEmpty()) {
                    var price = 250.0
                    val parts = trimmed.split(":")
                    if (parts.size == 2) {
                        price = parts[1].trim().toDoubleOrNull() ?: 250.0
                    }
                    val name = parts[0].trim()
                    result.add(PdfInvoiceItem(index++, name, 1, price, price))
                }
            }
        }

        if (materials.isNotBlank()) {
            materials.split(",").forEach { mat ->
                val trimmed = mat.trim()
                if (trimmed.isNotEmpty()) {
                    var qty = 1
                    var price = 150.0
                    var name = trimmed

                    if (trimmed.contains("x") && trimmed.contains("@")) {
                        try {
                            val namePart = trimmed.substringBefore("x").trim()
                            val qtyPart = trimmed.substringAfter("x").substringBefore("@").trim()
                            val pricePart = trimmed.substringAfter("@").trim()
                            name = namePart
                            qty = qtyPart.toIntOrNull() ?: 1
                            price = pricePart.toDoubleOrNull() ?: 150.0
                        } catch (_: Exception) {}
                    }
                    result.add(PdfInvoiceItem(index++, name, qty, price, qty * price))
                }
            }
        }

        if (result.isEmpty()) {
            result.add(PdfInvoiceItem(1, "General Vehicle Service & Inspection", 1, 350.0, 350.0))
        }

        return result
    }

    fun openPdf(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open Invoice PDF"))
        } catch (e: Exception) {
            Log.e(TAG, "Error opening PDF", e)
            Toast.makeText(context, "Saved PDF to: ${pdfFile.name}", Toast.LENGTH_LONG).show()
        }
    }

    fun sharePdf(context: Context, pdfFile: File, job: JobCardEntity) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Invoice GG-INV-${job.id} for Vehicle ${job.vehiclePlate}")
                putExtra(Intent.EXTRA_TEXT, "Dear ${job.customerName},\n\nPlease find attached the Tax Invoice for your vehicle ${job.vehicleName} (${job.vehiclePlate}).\nTotal Amount: ₹${String.format("%.0f", job.totalAmount)}\n\nThank you for choosing ${com.example.data.api.GarageSession.garageName.ifBlank { "Garage Guru" }}!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Invoice PDF"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing PDF", e)
            Toast.makeText(context, "Saved PDF to ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    fun printPdf(context: Context, pdfFile: File) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val jobName = "Invoice_${pdfFile.name}"
                printManager.print(
                    jobName,
                    object : PrintDocumentAdapter() {
                        override fun onLayout(
                            oldAttributes: PrintAttributes?,
                            newAttributes: PrintAttributes?,
                            cancellationSignal: CancellationSignal?,
                            callback: LayoutResultCallback?,
                            extras: Bundle?
                        ) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onLayoutCancelled()
                                return
                            }
                            val builder = PrintDocumentInfo.Builder(pdfFile.name)
                                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                .setPageCount(1)
                                .build()
                            callback?.onLayoutFinished(builder, true)
                        }

                        override fun onWrite(
                            pages: Array<out PageRange>?,
                            destination: ParcelFileDescriptor?,
                            cancellationSignal: CancellationSignal?,
                            callback: WriteResultCallback?
                        ) {
                            var input: FileInputStream? = null
                            var output: FileOutputStream? = null
                            try {
                                input = FileInputStream(pdfFile)
                                output = FileOutputStream(destination?.fileDescriptor)
                                input.copyTo(output)
                                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                            } catch (e: Exception) {
                                Log.e(TAG, "Print write error", e)
                                callback?.onWriteFailed(e.localizedMessage)
                            } finally {
                                try {
                                    input?.close()
                                    output?.close()
                                } catch (_: Exception) {}
                            }
                        }
                    },
                    PrintAttributes.Builder().build()
                )
            } else {
                Toast.makeText(context, "Printing service not available on this device", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Print error", e)
            Toast.makeText(context, "Error printing document: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
