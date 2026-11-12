package com.example.venusmonitor

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class RefreshWorker(c: Context, w: WorkerParameters) : Worker(c, w) {
    override fun doWork(): Result {
        Refresh.run(applicationContext)
        return Result.success()
    }
}
