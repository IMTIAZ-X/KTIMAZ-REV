package com.imtiaz.ktimazrev.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

// This class primarily serves as a conceptual placeholder
// for more complex threading/coroutine management on the Kotlin side.
// For this project, a simple dispatcher is sufficient, as heavy lifting is in C++.
object AppThreadPool {
    // A single-threaded dispatcher for sequential tasks if needed, or a cached thread pool
    val IO: CoroutineDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    val Default: CoroutineDispatcher = Dispatchers.Default // For CPU-bound tasks in Kotlin
    val Main: CoroutineDispatcher = Dispatchers.Main.immediate // For UI updates
}