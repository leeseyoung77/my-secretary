package com.luxboy.mysecretary.data.widget

import com.luxboy.mysecretary.data.preferences.AppPreferences
import com.luxboy.mysecretary.data.repository.EventRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): EventRepository
    fun preferences(): AppPreferences
}
