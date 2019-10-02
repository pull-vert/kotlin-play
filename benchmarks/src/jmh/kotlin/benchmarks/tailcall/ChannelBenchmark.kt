/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package benchmarks.tailcall

import coroutines.launch
import coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

// ./gradlew --no-daemon cleanJmhJar jmh -Pjmh="ChannelBenchmark"
//Benchmark                                     Mode  Cnt     Score     Error  Units
//ChannelBenchmark.cancellable                  avgt    5  4200,194 ± 118,223  us/op
//ChannelBenchmark.cancellableIterableReusable  avgt    5  1512,309 ±  36,607  us/op
//ChannelBenchmark.cancellableProducerReusable  avgt    5  1717,418 ±  32,162  us/op
//ChannelBenchmark.cancellableReusable          avgt    5  2580,033 ± 111,864  us/op
//ChannelBenchmark.nonCancellable               avgt    5  1545,899 ±  68,351  us/op
//ChannelBenchmark.nonCancellableIterable       avgt    5   728,980 ±  11,623  us/op
//ChannelBenchmark.nonCancellableProducer       avgt    5   976,064 ±  69,730  us/op




@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
open class ChannelBenchmark {

    private val iterations = 10_000

    @Volatile
    private var sink: Int = 0

//    @InternalCoroutinesApi
//    @Benchmark
//    fun kotlinxCancellable() = kotlinx.coroutines.runBlocking {
//        val ch = KotlinxCancellableChannel()
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
//    fun kotlinxNonCancellable() = kotlinx.coroutines.runBlocking {
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

    @Benchmark
    fun cancellable() = runBlocking {
        val ch = CancellableChannel()
        launch {
            repeat(iterations) { ch.send(it) }
        }

        launch {
            repeat(iterations) { sink = ch.receive() }
        }
    }

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

    @Benchmark
    fun cancellableProducerReusable() = runBlocking {
        val ch = CancellableProducerReusableChannel(0..iterations) { it }

        launch {
            repeat(iterations) {
                sink = ch.receive()
            }
        }
    }

    @Benchmark
    fun nonCancellableProducer() = runBlocking {
        val ch = NonCancellableProducerChannel(0..iterations) { it }

        launch {
            repeat(iterations) {
                sink = ch.receive()
            }
        }
    }

    @Benchmark
    fun cancellableIterableReusable() = runBlocking {
        val ch = CancellableIterableReusableChannel(0..iterations)

        launch {
            repeat(iterations) {
                sink = ch.receive()
            }
        }
    }

    @Benchmark
    fun nonCancellableIterable() = runBlocking {
        val ch = NonCancellableIterableChannel(0..iterations)

        launch {
            repeat(iterations) {
                sink = ch.receive()
            }
        }
    }
}
