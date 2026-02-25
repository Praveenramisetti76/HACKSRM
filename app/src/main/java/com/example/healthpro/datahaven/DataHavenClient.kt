package com.example.healthpro.datahaven

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * DataHaven Retrofit Client
 *
 * Singleton client for communicating with the Saathi DataHaven backend.
 * Uses long timeouts because DataHaven on-chain operations can take time.
 *
 * IMPORTANT: In production, replace BASE_URL with your deployed backend URL.
 * - For emulator: http://10.0.2.2:3001/api/
 * - For physical device on same network: http://<your-ip>:3001/api/
 */
object DataHavenClient {

    // Change this to your backend URL
    // Emulator uses 10.0.2.2 to reach host machine's localhost
    private const val BASE_URL = "http://10.0.2.2:3001/api/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        // DataHaven uploads involve on-chain txs, so we need longer timeouts
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)     // 5 min for upload + on-chain confirmation
        .writeTimeout(120, TimeUnit.SECONDS)     // 2 min for file upload
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: DataHavenApi = retrofit.create(DataHavenApi::class.java)
}
