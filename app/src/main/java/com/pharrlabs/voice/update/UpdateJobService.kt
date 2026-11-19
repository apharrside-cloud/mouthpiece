package com.pharrlabs.voice.update

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

/** Android batches the network check around the 12-hour interval to respect power and network restrictions. */
class UpdateJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!Prefs(this).updateCheck) return false
        work = scope.launch {
            val info = UpdateFetcher.latest()
            if (info != null && Prefs(this@UpdateJobService).updateCheck) {
                UpdateNotifier.record(this@UpdateJobService, info, notify = true)
            }
            work = null
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        work = null
        return false // The periodic schedule remains in place.
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        const val JOB_ID = 500

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!Prefs(context).updateCheck) {
                scheduler.cancel(JOB_ID)
                UpdateNotifier.cancel(context)
                return
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(UpdateCheck.INTERVAL_MS)
                .setPersisted(true)
                .build())
        }
    }
}
