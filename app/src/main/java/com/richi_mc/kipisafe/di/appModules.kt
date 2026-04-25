package com.richi_mc.kipisafe.di

import com.google.gson.GsonBuilder
import com.richi_mc.kipisafe.BuildConfig
import com.richi_mc.kipisafe.data.remote.KipiApiService
import com.richi_mc.kipisafe.data.stats.StatsDataSource
import com.richi_mc.kipisafe.ui.presentation.home.HomeViewModel
import com.richi_mc.kipisafe.ui.presentation.metrics.MetricsViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

val networkModule = module {

    single {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            clientBuilder.addInterceptor(logging)
        }

        val gson = GsonBuilder().create()

         Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    single {
        get<Retrofit>()
            .create(KipiApiService::class.java)
    }
}
val dataModule = module {
    factory {
        StatsDataSource(context = androidContext())
    }
}
val viewModelModule = module {
    viewModel {
        MetricsViewModel(context = androidContext(), get())
    }
    viewModel {
        HomeViewModel(context = androidContext())
    }
}

val appModules = listOf(networkModule, viewModelModule, dataModule)