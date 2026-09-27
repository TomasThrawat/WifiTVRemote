package com.tomasthrawat.wifitvremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteUiBehaviorTest {
    @Test
    fun referenceCanvas_isFixedLtrAndHomeIsOnReferenceSide() {
        assertEquals(LayoutDirectionPolicy.LTR, RemoteUiSpec.coordinateLayoutDirection)
        assertEquals(154, RemoteUiSpec.homeX)
        assertEquals(100, RemoteUiSpec.playX)
    }

    @Test
    fun quickActions_areActionableAndMapToExpectedCommands() {
        assertEquals(
            listOf(
                RemoteQuickAction.BACK,
                RemoteQuickAction.HOME,
                RemoteQuickAction.NUMBER_PAD
            ),
            RemoteUiSpec.quickActions
        )
        assertTrue(RemoteUiSpec.quickActions.all(RemoteUiSpec::isActionable))
    }

    @Test
    fun rockerSegments_haveReferenceTouchTarget() {
        assertEquals(32, RemoteUiSpec.rockerSegmentHeightDp)
    }
}
