package com.archery.tracker.di

import android.content.Context
import androidx.room.Room
import com.archery.tracker.data.local.ArcheryDatabase
import com.archery.tracker.data.remote.ArcheryApi
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val BASE_URL = "https://archeryapp-60081207448.development.catalystserverless.in/server/api/"

class AppContainer(context: Context) {

    private val database: ArcheryDatabase = Room.databaseBuilder(
        context.applicationContext, ArcheryDatabase::class.java, "archery.db",
    ).build()

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder().build()

    private val api: ArcheryApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ArcheryApi::class.java)

    val repository: ArcheryRepository = ArcheryRepository(database.archeryDao(), api)
}
