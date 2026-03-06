package com.zionchat.app.zicode.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.ziCodeDataStore: DataStore<Preferences> by preferencesDataStore(name = "zicode_v2")
