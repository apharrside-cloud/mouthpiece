package com.pharrlabs.voice.history

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.pharrlabs.voice.settings.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Prune even when History isn't opened. Android may defer this daily job while the phone sleeps. */
class HistoryCleanupService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = scope.launch {
            kotlinx.coroutines.withContext(Dispatchers.IO) { Prefs(this@HistoryCleanupService).history().all() }
            work = null
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); work = null; return false }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val JOB_ID = 501
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, HistoryCleanupService::class.java))
                .setPeriodic(24L * 60 * 60 * 1000).setPersisted(true).build())
        }
    }
}
