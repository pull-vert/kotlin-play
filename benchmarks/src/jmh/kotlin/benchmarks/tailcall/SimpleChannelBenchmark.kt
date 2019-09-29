/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package benchmarks.tailcall

import coroutines.launch
import coroutines.runBlocking
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.openjdk.jmh.annotations.*
import java.util.concurrent.*

// ./gradlew --no-daemon cleanJmhJar jmh -Pjmh="SimpleChannelBenchmark"
//Benchmark                                     Mode  Cnt     Score     Error  Units
//SimpleChannelBenchmark.NonCancellable         avgt    5   374,729 ▒   7,718  us/op
//SimpleChannelBenchmark.cancellable            avgt    5  3445,842 ▒ 266,652  us/op
//SimpleChannelBenchmark.cancellableReusable    avgt    5  1895,527 ▒  62,275  us/op
//SimpleChannelBenchmark.kotlinxCancellable     avgt    5  3345,329 ▒ 234,224  us/op
//SimpleChannelBenchmark.kotlinxNonCancellable  avgt    5  1232,176 ▒  45,612  us/op

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
open class SimpleChannelBenchmark {

    private val iterations = 10_000

    @Volatile
    private var sink: Int = 0

    @InternalCoroutinesApi
    @Benchmark
    fun kotlinxCancellable() = kotlinx.coroutines.runBlocking {
        val ch = KotlinxCancellableChannel()
        launch {
            repeat(iterations) { ch.send(it) }
        }

        launch {
            repeat(iterations) { sink = ch.receive() }
        }
    }

    @Benchmark
    fun kotlinxNonCancellable() = kotlinx.coroutines.runBlocking {
        val ch = NonCancellableChannel()
        launch {
            repeat(iterations) { ch.send(it) }
        }

        launch {
            repeat(iterations) {
                sink = ch.receive()
            }
        }
    }

//    @Benchmark
//    fun cancellable() = runBlocking {
//        val ch = CancellableChannel()
//        launch {
//            repeat(iterations) { ch.send(it) }
//        }
//
//        launch {
//            repeat(iterations) { sink = ch.receive() }
//        }
//    }
//
//    @Benchmark
//    fun cancellableReusable() = runBlocking {
//        val ch = CancellableReusableChannel()
//        launch {
//            repeat(iterations) { ch.send(it) }
//        }
//
//        launch {
//            repeat(iterations) { sink = ch.receive() }
//        }
//    }
//
//    @Benchmark
//    fun NonCancellable() = runBlocking {
//        val ch = NonCancellableChannel()
//        launch {
//            repeat(iterations) { ch.send(it) }
//        }
//
//        launch {
//            repeat(iterations) {
//                sink = ch.receive()
//            }
//        }
//    }
}
