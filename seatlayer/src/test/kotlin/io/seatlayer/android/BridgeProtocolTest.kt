package io.seatlayer.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeProtocolTest {
    @Test
    fun choosesHighestSharedProtocol() {
        assertEquals(
            1,
            negotiate(
                host = ProtocolRange(1, 3),
                web = ProtocolRange(1, 1),
            ),
        )
    }

    @Test
    fun rejectsDisjointRanges() {
        assertNull(
            negotiate(
                host = ProtocolRange(2, 4),
                web = ProtocolRange(1, 1),
            ),
        )
    }
}
