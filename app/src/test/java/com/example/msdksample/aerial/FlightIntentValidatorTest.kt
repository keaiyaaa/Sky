package com.example.msdksample.aerial

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightIntentValidatorTest {
    @Test fun validDirectionalMoveRequiresFrameDistanceAndSpeed() {
        assertTrue(FlightIntentValidator.validate(FlightIntent("fly_to", FlightParameters(
            direction = "forward", distance = 10.0, speed = 2.0, referenceFrame = "body"
        ))).valid)
        assertFalse(FlightIntentValidator.validate(FlightIntent("fly_to", FlightParameters(
            direction = "forward", distance = 10.0
        ))).valid)
    }

    @Test fun takeoffDoesNotAcceptInventedTargetAltitude() {
        assertFalse(FlightIntentValidator.validate(FlightIntent("takeoff", FlightParameters(altitude = 10.0))).valid)
        assertTrue(FlightIntentValidator.validate(FlightIntent("takeoff", FlightParameters())).valid)
    }

    @Test fun unknownActionAndInvalidNumbersAreRejected() {
        assertFalse(FlightIntentValidator.validate(FlightIntent("surprise", FlightParameters())).valid)
        assertFalse(FlightIntentValidator.validate(FlightIntent("fly_to", FlightParameters(
            direction = "forward", distance = Double.NaN, speed = 2.0, referenceFrame = "body"
        ))).valid)
        assertFalse(FlightIntentValidator.validate(FlightIntent("fly_to", FlightParameters(
            direction = "forward", distance = 100000.0, speed = 2.0, referenceFrame = "body"
        ))).valid)
    }

    @Test fun supportedProfessionalActionsGetExplicitDisposition() {
        val stop = FlightIntentValidator.validate(FlightIntent("stop_return_home", FlightParameters()))
        assertTrue(stop.valid)
        assertFalse(stop.executableNow)
        assertTrue(FlightIntentValidator.validate(FlightIntent("rotate", FlightParameters(
            yaw = 90.0, referenceFrame = "body"
        ))).valid)
        assertFalse(FlightIntentValidator.validate(FlightIntent("rotate", FlightParameters(
            yaw = 400.0, referenceFrame = "body"
        ))).valid)
    }

    @Test fun unsupportedMultiPhotoCountIsRejected() {
        assertFalse(FlightIntentValidator.validate(FlightIntent("photo", FlightParameters(count = 3))).valid)
        assertFalse(FlightIntentValidator.validate(FlightIntent("photo", FlightParameters(distance = 10.0))).valid)
        assertFalse(FlightIntentValidator.validate(FlightIntent("gimbal_control", FlightParameters(pitch = 0.0, yaw = 999.0))).valid)
    }
}
