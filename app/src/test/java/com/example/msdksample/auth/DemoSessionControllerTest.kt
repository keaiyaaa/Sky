package com.example.msdksample.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSessionControllerTest {
    @Test fun demoSignInRejectsBlankAndKeepsOnlyInMemoryName() {
        val controller = DemoSessionController()
        assertFalse(controller.signIn("  "))
        assertNull(controller.signedInUser)
        assertTrue(controller.signIn("巡检员演示账号"))
        assertEquals("巡检员演示账号", controller.signedInUser)
    }

    @Test fun expiryAndSignOutClearCurrentDemoUser() {
        val controller = DemoSessionController()
        controller.signIn("演示用户")
        controller.expire()
        assertNull(controller.signedInUser)
        controller.signIn("演示用户")
        controller.signOut()
        assertNull(controller.signedInUser)
    }
}
