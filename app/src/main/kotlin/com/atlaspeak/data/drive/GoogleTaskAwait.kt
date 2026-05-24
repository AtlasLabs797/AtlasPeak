package com.atlaspeak.data.drive

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            task.isSuccessful -> continuation.resume(task.result)
            task.exception != null -> continuation.resumeWithException(task.exception!!)
            else -> continuation.resumeWithException(IllegalStateException("Google task failed without an exception"))
        }
    }
}
