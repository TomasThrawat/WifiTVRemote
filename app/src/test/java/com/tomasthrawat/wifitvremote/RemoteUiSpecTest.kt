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
            listOf("CH-LIST", "Ch +", "Ch -"),
            RemoteUiSpec.channelControls
        )
        assertEquals(
            listOf("Back", "Home", "123"),
            RemoteUiSpec.quickActions
        )
        assertEquals(
            listOf("Remote", "Apps", "Cast", "Settings"),
            RemoteUiSpec.bottomNavigation
        )
    }
}
