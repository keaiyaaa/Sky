package com.example.msdksample.auth

/** In-memory demo only. It does not authenticate users or persist credentials. */
class DemoSessionController {
    var signedInUser: String? = null
        private set

    fun signIn(demoName: String): Boolean {
        val normalized = demoName.trim()
        if (normalized.isEmpty()) return false
        signedInUser = normalized
        return true
    }

    fun expire() {
        signedInUser = null
    }

    fun signOut() {
        signedInUser = null
    }
}

object DemoSession {
    private val controller = DemoSessionController()
    val signedInUser: String? get() = controller.signedInUser
    fun signIn(demoName: String) = controller.signIn(demoName)
    fun expire() = controller.expire()
    fun signOut() = controller.signOut()
}
