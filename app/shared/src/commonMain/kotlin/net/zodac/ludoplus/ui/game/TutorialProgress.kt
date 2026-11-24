package net.zodac.ludoplus.ui.game

import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.ludoplus.game.TutorialScript
import net.zodac.ludoplus.game.TutorialStep

/**
 * Where a guided game ([script]) is: the [part] being played and the [step] in it waited for ([current]), and the step
 * the screen shows ([shown]), which follows once the action before it has played out.
 */
internal class TutorialProgress(val script: TutorialScript) {
    val part = MutableStateFlow(0)

    /** The index in [part]'s steps of the one waited for: past its last once the part is done. */
    var step = 0
        private set

    val shown = MutableStateFlow<TutorialStep?>(null)

    /** Whether a tap on Next is taken now: a step that waits for one is shown, and its action not yet taken. */
    val nextReady = MutableStateFlow(false)

    /** The step waited for, or null once [part]'s last is done. */
    val current: TutorialStep?
        get() = script.parts[part.value].steps.getOrNull(step)

    /** [part] from its first step, shown at once. */
    fun begin(part: Int) {
        this.part.value = part
        step = 0
        show()
    }

    /** Shows the step waited for. */
    fun show() {
        shown.value = current
        nextReady.value = current?.waitsForNext == true
    }

    /** The step waited for is done; the caller [show]s the one after it. */
    fun next() {
        step++
        nextReady.value = false
    }
}
