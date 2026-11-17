package net.zodac.ludo.ui.game

import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.ludo.game.TutorialScript
import net.zodac.ludo.game.TutorialStep

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

    /** The step waited for, or null once [part]'s last is done. */
    val current: TutorialStep?
        get() = script.parts[part.value].steps.getOrNull(step)

    /** [part] from its first step, shown at once. */
    fun begin(part: Int) {
        this.part.value = part
        step = 0
        shown.value = current
    }

    /** The step waited for is done; [shown] is the caller's to move on. */
    fun next() {
        step++
    }
}
