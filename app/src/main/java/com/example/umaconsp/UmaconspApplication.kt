package com.example.umaconsp

import android.app.Application
import android.content.Context
import com.example.umaconsp.data.database.AppDatabase

class UmaconspApplication : Application() {

    companion object {

        lateinit var instance: UmaconspApplication


        val database: AppDatabase by lazy { AppDatabase.getDatabase(instance) }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    fun getAppContext(): Context = applicationContext
}