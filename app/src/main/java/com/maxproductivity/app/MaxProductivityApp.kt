package com.maxproductivity.app

import android.app.Application
import com.maxproductivity.app.data.AppPreferencesRepository

class MaxProductivityApp : Application() {
    lateinit var preferencesRepository: AppPreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = AppPreferencesRepository(this)
    }

    companion object {
        fun get(context: android.content.Context): MaxProductivityApp =
            context.applicationContext as MaxProductivityApp
    }
}
