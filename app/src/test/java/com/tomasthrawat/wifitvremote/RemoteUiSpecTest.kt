package com.tomasthrawat.wifitvremote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteUiSpecTest {
    @Test
    fun remoteControlLabels_matchReferenceLayout() {
        assertEquals(
            listOf("Vol +", "Mute", "Vol -"),
            RemoteUiSpec.volumeControls
        )
        assertEquals(
            listOf("Ch +", "Menu", "Ch -"),
            RemoteUiSpec.channelControls
        )
        assertEquals(
            listOf("Back", "Home", "Power"),
            RemoteUiSpec.quickActions
        )
        assertEquals(
            listOf("Remote", "Apps", "Settings"),
            RemoteUiSpec.bottomNavigation
        )
    }
}
