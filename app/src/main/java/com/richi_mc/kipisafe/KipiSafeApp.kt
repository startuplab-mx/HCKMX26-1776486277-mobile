package com.richi_mc.kipisafe

import android.app.Application
import com.richi_mc.kipisafe.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KipiSafeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KipiSafeApp)
            modules(appModules)
        }
    }
}