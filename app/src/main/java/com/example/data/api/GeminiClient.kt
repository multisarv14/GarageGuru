package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun getDiagnostics(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    suspend fun troubleshootSymptoms(symptoms: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return """
                🔧 [Local Diagnostics Mode - Key Not Configured]
                
                The AI Diagnostics Assistant is active! To enable real live Gemini AI suggestions:
                1. Open the Secrets Panel in AI Studio.
                2. Enter your real google 'GEMINI_API_KEY'.
                
                Based on traditional diagnostic lookup, here is physical standard advice for:
                "$symptoms"
                
                - Check battery voltage and alternator charging output.
                - Review fluid levels including coolant pressure and brake hydraulics.
                - Verify physical connections, sensor terminals, and scanner trouble codes (OBD-II).
                - Test spark plug firing resistance or glow plug relays.
            """.trimIndent()
        }

        val prompt = """
            You are "Garage Guru AI Assistant", an elite automotive diagnostics expert.
            Analyze these vehicle symptoms and provide:
            1. Likely Diagnostic Causes.
            2. Troubleshooting Procedure (Step-by-Step).
            3. Estimated Repairs & Recommended Spare Parts to replace.
            
            Keep the response formatting clear and highly structured with bullet points.
            Symptom details: $symptoms
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are a professional Swiss automotive garage diagnostics assistant. Always be precise, authoritative, and helpful."))
            )
        )

        return try {
            val response = service.getDiagnostics(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No diagnostics response from Garage Guru AI. Please try again."
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI troubleshooting", e)
            """
                ⚠️ [AI Offline/Error]
                
                Failed to query live Google AI Service: ${e.localizedMessage}
                
                Local offline troubleshooting advice for "$symptoms":
                - Inspect underlying engine components, hose linkages, and electrical ground wires.
                - Read vehicle Diagnostic Trouble Codes (OBD-II) using a standard scanner.
                - Check for fluid leaks, blockages, or mechanical wear.
            """.trimIndent()
        }
    }
}
