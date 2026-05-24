package com.atlaspeak.di

import com.atlaspeak.BuildConfig
import com.atlaspeak.data.drive.DriveApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().apply {
            connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            readTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
            writeTimeout(Duration.ofSeconds(WRITE_TIMEOUT_SECONDS))
            callTimeout(Duration.ofSeconds(CALL_TIMEOUT_SECONDS))
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }.build()
    }

    @Provides
    @Singleton
    fun provideDriveRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideDriveApiService(retrofit: Retrofit): DriveApiService = retrofit.create(DriveApiService::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_SECONDS = 60L
    private const val WRITE_TIMEOUT_SECONDS = 60L
    private const val CALL_TIMEOUT_SECONDS = 120L
}
