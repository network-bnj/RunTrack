package com.runtrack.sensors

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/** Sube un resumen de la sesión a Cloud Firestore. Si Firebase no está configurado, se omite sin error. */
class CloudSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (FirebaseApp.getApps(applicationContext).isEmpty()) {
            Log.w("CloudSync", "Firebase no configurado (falta google-services.json); sincronización omitida")
            return Result.success()
        }
        val s = SensorRepository.state.value
        val doc = hashMapOf(
            "timestamp" to System.currentTimeMillis(),
            "directionChanges" to s.directionChanges,
            "accelRaw" to s.accelRaw, "accelAccepted" to s.accelAccepted,
            "gyroRaw" to s.gyroRaw, "gyroAccepted" to s.gyroAccepted
        )
        return try {
            Tasks.await(FirebaseFirestore.getInstance().collection("runtrack_sessions").add(doc))
            Result.success()
        } catch (e: Exception) {
            Log.e("CloudSync", "Fallo al subir", e)
            Result.retry()                       // WorkManager reintenta con backoff exponencial
        }
    }
}
