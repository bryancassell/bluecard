package io.github.bryancassell.bluecard.data

import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * iOS's [BackgroundTime]: a UIKit background task for each piece of work, which Apple's
 * "Extending your app's background execution time" asks for before work that must finish:
 * https://developer.apple.com/documentation/uikit/extending-your-app-s-background-execution-time
 *
 * If the time runs out first, the task ends, since UIKit ends an app that doesn't end its tasks
 * then. The work goes on until iOS suspends the app, and finishes when the scout comes back, or
 * is lost if iOS ends the app first. A Room write is lost whole then, since it's one transaction.
 */
internal class UIKitBackgroundTime(private val tasks: BackgroundTasks) : BackgroundTime {
    override suspend fun <T> whileRunning(block: suspend () -> T): T {
        val task = HeldTask(tasks)
        try {
            return block()
        } finally {
            task.end()
        }
    }
}

/** One background task, which ends once, whether time runs out first or the work finishes. */
private class HeldTask(private val tasks: BackgroundTasks) {
    // The expiration handler runs on the main thread, and the work may finish on any.
    private val lock = Lock()
    private var task: UIBackgroundTaskIdentifier? = null
    private var ended = false

    init {
        // UIKit refuses one when the app is out of background time.
        val begun = tasks.begin(TASK_NAME, ::end).takeIf { it != UIBackgroundTaskInvalid }
        // If the time ran out before begin returned, as it can when work begins off the main
        // thread, the expiration handler found no task to end, so it's ended here.
        val endedFirst = lock.withLock { ended.also { if (!it) task = begun } }
        if (endedFirst && begun != null) tasks.end(begun)
    }

    fun end() {
        val ending = lock.withLock {
            ended = true
            task.also { task = null }
        }
        if (ending != null) tasks.end(ending)
    }

    private companion object {
        // Names it in UIKit's log, such as when it ends tasks whose time ran out.
        const val TASK_NAME = "BlueCard outliving work"
    }
}

/**
 * Begins and ends UIKit's background tasks. An interface, so tests can see them: Gradle runs
 * iOS tests as a plain program on the simulator, not as an app, so [UIApplication]'s
 * `sharedApplication` is nil there, and UIKit's refuses every task.
 */
internal interface BackgroundTasks {
    /** Begins a task named [name], calling [expired] on the main thread if its time runs out. */
    fun begin(name: String, expired: () -> Unit): UIBackgroundTaskIdentifier

    /** Ends [task]. */
    fun end(task: UIBackgroundTaskIdentifier)
}

/** UIKit's, from the app's [UIApplication]. */
internal object UIApplicationBackgroundTasks : BackgroundTasks {
    override fun begin(name: String, expired: () -> Unit): UIBackgroundTaskIdentifier =
        UIApplication.sharedApplication.beginBackgroundTaskWithName(name, expired)

    override fun end(task: UIBackgroundTaskIdentifier) {
        UIApplication.sharedApplication.endBackgroundTask(task)
    }
}
