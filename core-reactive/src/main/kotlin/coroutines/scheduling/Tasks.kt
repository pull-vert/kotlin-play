package coroutines.scheduling

/**
 * An interface to signal the completion of a Task.
 *
 *  The invocation of `complete` completes the task.
 *
 * @apiNote `DeferredCompleter` is useful when a task is not necessary
 * complete when its `run` method returns, but will complete at a
 * later time, and maybe in different thread.
 */
@FunctionalInterface
internal interface DeferredCompleter {

    /** Completes the task. Must be called once, and once only.  */
    abstract fun complete()
}

/**
 * A restartable task.
 */
@FunctionalInterface
internal interface CompletableTask {

    /**
     * The body of the task.
     *
     * @param taskCompleter
     * A completer that must be invoked once, and only once,
     * when this task is logically finished
     */
    fun run(taskCompleter: DeferredCompleter)
}
