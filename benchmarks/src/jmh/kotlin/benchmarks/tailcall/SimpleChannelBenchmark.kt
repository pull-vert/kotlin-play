/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package benchmarks.tailcall

import coroutines.launch
import coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import java.util.concurrent.*

//Benchmark                                   Mode  Cnt     Score    Error  Units
//SimpleChannelBenchmark.cancellableReusable  avgt    5  1933,004 ▒ 30,571  us/op
//SimpleChannelBenchmark.nonCancellable       avgt    5   366,017 ▒ 17,627  us/op

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

    @Benchmark
    fun cancellableReusable() = runBlocking {
        val ch = CancellableReusableChannel()
        launch {
            repeat(iterations) { ch.send(it) }
        }

        launch {
            repeat(iterations) { sink = ch.receive() }
        }
    }

    @Benchmark
    fun nonCancellable() = runBlocking {
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
}
