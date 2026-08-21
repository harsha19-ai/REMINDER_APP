package com.example.reminderapp.data

import android.content.Context

object RepositoryProvider {
    private var repository: DataRepository? = null

    fun getRepository(context: Context): DataRepository {
        return repository ?: synchronized(this) {
            repository ?: DefaultDataRepository(context.applicationContext).also { repository = it }
        }
    }
}
