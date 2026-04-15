package com.danielag_nutritrack.app.api

import com.google.gson.annotations.SerializedName
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class IntervalsWellness(
    val id: String? = null,
    val weight: Float? = null,
    @SerializedName("restingHR") val restingHR: Int? = null,
    val hrv: Float? = null,
    @SerializedName("hrvSDNN") val hrvSDNN: Float? = null,
    val steps: Int? = null
)

data class IntervalsActivity(
    val id: String? = null,
    val name: String? = null,
    @SerializedName("start_date_local") val startDateLocal: String? = null,
    val type: String? = null,
    val calories: Int? = null,
    @SerializedName("moving_time") val movingTime: Int? = null
)

interface IntervalsService {

    @GET("api/v1/athlete/{id}/wellness/{date}")
    suspend fun getWellness(
        @Path("id") athleteId: String,
        @Path("date") date: String  // ISO-8601 date e.g. "2026-04-15"
    ): IntervalsWellness

    @GET("api/v1/athlete/{id}/activities")
    suspend fun getActivities(
        @Path("id") athleteId: String,
        @Query("oldest") oldest: String,
        @Query("newest") newest: String
    ): List<IntervalsActivity>

    companion object {
        private const val BASE_URL = "https://intervals.icu/"

        fun create(apiKey: String): IntervalsService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val credentials = Credentials.basic("API_KEY", apiKey)
                    val request = chain.request().newBuilder()
                        .header("Authorization", credentials)
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            return retrofit.create(IntervalsService::class.java)
        }
    }
}
