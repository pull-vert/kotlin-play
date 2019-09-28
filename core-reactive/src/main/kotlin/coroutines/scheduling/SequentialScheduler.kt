//package coroutines.scheduling
//
//import kotlinx.atomicfu.atomic
//import kotlinx.atomicfu.loop
//import java.util.concurrent.Executor
//
///**
// * A scheduler of ( repeatable ) tasks that MUST be run sequentially.
// *
// *
// *  This class can be used as a synchronization aid that assists a number of
// * parties in running a task in a mutually exclusive fashion.
// *
// *
// *  To run the task, a party invokes `runOrSchedule`. To permanently
// * prevent the task from subsequent runs, the party invokes `stop`.
// *
// *
// *  The parties can, but do not have to, operate in different threads.
// *
// *
// *  The task can be either synchronous ( completes when its `run`
// * method returns ), or asynchronous ( completed when its
// * `DeferredCompleter` is explicitly completed ).
// *
// *
// *  The next run of the task will not begin until the previous run has
// * finished.
// *
// *
// *  The task may invoke `runOrSchedule` itself, which may be a normal
// * situation.
// */
//class SequentialScheduler(private val restartableTask: RestartableTask) {
//
//    private val _state = atomic(END)
//    private val completer: DeferredCompleter = TryEndDeferredCompleter()
//    private val schedulableTask = SchedulableTask()
//
//    /**
//     * Tells whether, or not, this scheduler has been permanently stopped.
//     *
//     *
//     *  Should be used from inside the task to poll the status of the
//     * scheduler, pretty much the same way as it is done for threads:
//     * <pre>`if (!Thread.currentThread().isInterrupted()) {
//     * ...
//     * }
//    `</pre> *
//     */
//    val isStopped: Boolean
//        get() = _state.value == STOP
//
//    /*
//       Since the task is fixed and known beforehand, no blocking synchronization
//       (locks, queues, etc.) is required. The job can be done solely using
//       nonblocking primitives.
//       The machinery below addresses two problems:
//         1. Running the task in a sequential order (no concurrent runs):
//                begin, end, begin, end...
//         2. Avoiding indefinite recursion:
//                begin
//                  end
//                    begin
//                      end
//                        ...
//       Problem #1 is solved with a finite state machine with 4 states:
//           BEGIN, AGAIN, END, and STOP.
//       Problem #2 is solved with a "state modifier" OFFLOAD.
//       Parties invoke `runOrSchedule()` to signal the task must run. A party
//       that has invoked `runOrSchedule()` either begins the task or exploits the
//       party that is either beginning the task or ending it.
//       The party that is trying to end the task either ends it or begins it
//       again.
//       To avoid indefinite recursion, before re-running the task the
//       TryEndDeferredCompleter sets the OFFLOAD bit, signalling to its "child"
//       TryEndDeferredCompleter that this ("parent") TryEndDeferredCompleter is
//       available and the "child" must offload the task on to the "parent". Then
//       a race begins. Whichever invocation of TryEndDeferredCompleter.complete
//       manages to unset OFFLOAD bit first does not do the work.
//       There is at most 1 thread that is beginning the task and at most 2
//       threads that are trying to end it: "parent" and "child". In case of a
//       synchronous task "parent" and "child" are the same thread.
//     */
//
//    /**
//     * An interface to signal the completion of a [RestartableTask].
//     *
//     *
//     *  The invocation of `complete` completes the task. The invocation
//     * of `complete` may restart the task, if an attempt has previously
//     * been made to run the task while it was already running.
//     *
//     * @apiNote `DeferredCompleter` is useful when a task is not necessary
//     * complete when its `run` method returns, but will complete at a
//     * later time, and maybe in different thread. This type exists for
//     * readability purposes at use-sites only.
//     */
//    abstract class DeferredCompleter internal constructor() { // Extensible from this (outer) class ONLY.
//
//        /** Completes the task. Must be called once, and once only.  */
//        abstract fun complete()
//    }
//
//    /**
//     * A restartable task.
//     */
//    @FunctionalInterface
//    interface RestartableTask {
//
//        /**
//         * The body of the task.
//         *
//         * @param taskCompleter
//         * A completer that must be invoked once, and only once,
//         * when this task is logically finished
//         */
//        fun run(taskCompleter: DeferredCompleter)
//    }
//
//    /**
//     * A simple and self-contained task that completes once its `run`
//     * method returns.
//     */
//    abstract class CompleteRestartableTask : RestartableTask {
//        override fun run(taskCompleter: DeferredCompleter) {
//            try {
//                run()
//            } finally {
//                taskCompleter.complete()
//            }
//        }
//
//        /** The body of the task.  */
//        protected abstract fun run()
//    }
//
//    /**
//     * A task that runs its main loop within a synchronized block to provide
//     * memory visibility between runs. Since the main loop can't run concurrently,
//     * the lock shouldn't be contended and no deadlock should ever be possible.
//     */
//    class SynchronizedRestartableTask internal constructor(private val mainLoop: Runnable) : CompleteRestartableTask() {
//        private val lock = Any()
//
//        override fun run() {
//            synchronized(lock) {
//                mainLoop.run()
//            }
//        }
//    }
//
//    /**
//     * An auxiliary task that starts the restartable task:
//     * `restartableTask.run(completer)`.
//     */
//    private inner class SchedulableTask : Runnable {
//        override fun run() {
//            restartableTask.run(completer)
//        }
//    }
//
//    /**
//     * Runs or schedules the task to be run.
//     *
//     * @implSpec The recursion which is possible here must be bounded:
//     *
//     * <pre>`this.runOrSchedule()
//     * completer.complete()
//     * this.runOrSchedule()
//     * ...
//    `</pre> *
//     *
//     * @implNote The recursion in this implementation has the maximum
//     * depth of 1.
//     */
//    fun runOrSchedule() {
//        runOrSchedule(schedulableTask, null)
//    }
//
//    /**
//     * Executes or schedules the task to be executed in the provided executor.
//     *
//     *
//     *  This method can be used when potential executing from a calling
//     * thread is not desirable.
//     *
//     * @param executor
//     * An executor in which to execute the task, if the task needs
//     * to be executed.
//     *
//     * @apiNote The given executor can be `null` in which case calling
//     * `runOrSchedule(null)` is strictly equivalent to calling
//     * `runOrSchedule()`.
//     */
//    fun runOrSchedule(executor: Executor) {
//        runOrSchedule(schedulableTask, executor)
//    }
//
//    private fun runOrSchedule(task: SchedulableTask, executor: Executor?) {
//        _state.loop { state ->
//            when {
//                state == END ->
//                    if (_state.compareAndSet(END, BEGIN)) {
//                        if (executor == null) {
//                            task.run()
//                        } else {
//                            executor.execute(task)
//                        }
//                        return
//                    }
//                state and BEGIN != 0 -> // Tries to change the state to AGAIN, preserving OFFLOAD bit
//                    if (_state.compareAndSet(state, AGAIN or (state and OFFLOAD))) return
//                state and AGAIN != 0 || state == STOP ->
//                    /* In the case of AGAIN the scheduler does not provide
//                   happens-before relationship between actions prior to
//                   runOrSchedule() and actions that happen in task.run().
//                   The reason is that no volatile write is done in this case,
//                   and the call piggybacks on the call that has actually set
//                   AGAIN state. */
//                    return
//                else -> // Non-existent state, or the one that cannot be offloaded
//                    error("Inconsistent state $state")
//            }
//        }
//
//    }
//
//    /** The only concrete `DeferredCompleter` implementation.  */
//    private inner class TryEndDeferredCompleter : DeferredCompleter() {
//
//        override fun complete() {
//                offLoad()
//                _state.loop { state ->
//                    when {
//                        state and OFFLOAD != 0 ->
//                            /* OFFLOAD bit can never be observed here. Otherwise
//                           it would mean there is another invocation of
//                           "complete" that can run the task. */
//                            error("FFLOAD bit can never be observed here, state = $state")
//                        state == BEGIN -> if (_state.compareAndSet(BEGIN, END)) return
//                        state == AGAIN ->
//                            if (_state.compareAndSet(AGAIN, BEGIN or OFFLOAD)) {
//                                restartableTask.run(completer)
//                            }
//                        state == STOP -> return
//                        else -> // Non-existent state
//                            error("Inconsistent state $state")
//                    }
//                }
//        }
//
//        private fun offLoad() {
//            _state.loop { state ->
//                if (state and OFFLOAD != 0) {
//                    // Tries to offload ending of the task to the parent
//                    if (_state.compareAndSet(state, state and OFFLOAD.inv())) {
//                        return
//                    }
//                } else {
//                    return
//                }
//            }
//        }
//    }
//
//    /**
//     * Stops this scheduler.  Subsequent invocations of `runOrSchedule`
//     * are effectively no-ops.
//     *
//     *
//     *  If the task has already begun, this invocation will not affect it,
//     * unless the task itself uses `isStopped()` method to check the state
//     * of the handler.
//     */
//    fun stop() {
//        _state.getAndSet(STOP)
//    }
//
//    companion object {
//
//        private const val OFFLOAD = 1
//        private const val AGAIN = 2
//        private const val BEGIN = 4
//        private const val STOP = 8
//        private const val END = 16
//
//        /**
//         * Returns a new `SequentialScheduler` that executes the provided
//         * `mainLoop` from within a [SynchronizedRestartableTask].
//         *
//         * @apiNote This is equivalent to calling
//         * `new SequentialScheduler(new SynchronizedRestartableTask(mainLoop))`
//         * The main loop must not perform any blocking operation.
//         *
//         * @param mainLoop The main loop of the new sequential scheduler
//         * @return a new `SequentialScheduler` that executes the provided
//         * `mainLoop` from within a [SynchronizedRestartableTask].
//         */
//        fun synchronizedScheduler(mainLoop: Runnable): SequentialScheduler {
//            return SequentialScheduler(SynchronizedRestartableTask(mainLoop))
//        }
//    }
//}
