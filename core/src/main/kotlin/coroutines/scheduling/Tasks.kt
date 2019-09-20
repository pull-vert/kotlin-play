package coroutines.scheduling

internal enum class TaskMode {

    /**
     * Marker indicating that task is CPU-bound and will not block
     */
    NON_BLOCKING,

    /**
     * Marker indicating that task may potentially block, thus giving scheduler a hint that additional thread may be required
     */
    PROBABLY_BLOCKING,
}

internal interface SchedulerTaskContext {
    val taskMode: TaskMode
    fun afterTask()
}

internal object NonBlockingContext : SchedulerTaskContext {
    override val taskMode: TaskMode = TaskMode.NON_BLOCKING

    override fun afterTask() {
        // Nothing for non-blocking context
    }
}

internal abstract class SchedulerTask(
        @JvmField var submissionTime: Long,
        @JvmField var taskContext: SchedulerTaskContext
) : Runnable {
    constructor() : this(0, NonBlockingContext)
    val mode: TaskMode get() = taskContext.taskMode
}
