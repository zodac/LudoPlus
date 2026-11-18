package net.zodac.ludoplus.data.settings

/** One setting the cloud save carries, as the type it is stored as. A null in its place means unset. */
sealed interface SettingValue {

    data class Text(val value: String) : SettingValue

    data class Number(val value: Int) : SettingValue

    data class Flag(val value: Boolean) : SettingValue

    data class TextSet(val value: Set<String>) : SettingValue {
        /** In order, so two equal sets read the same whichever order they were built in: a merge's tie-break compares it. */
        override fun toString(): String = "TextSet(${value.sorted()})"
    }
}
