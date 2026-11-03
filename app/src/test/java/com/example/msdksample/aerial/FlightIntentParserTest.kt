package com.example.msdksample.aerial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FlightIntentParserTest {
    @Test fun parsesFencedJsonAndValidatesProfessionalFields() {
        val result = FlightIntentParser.parse("```json\n{\"flightType\":\"fly_to\",\"parameters\":{\"referenceFrame\":\"body\",\"direction\":\"forward\",\"distance\":10,\"speed\":2}}\n```")
        assertEquals("fly_to", result.flightType)
        assertEquals(10.0, result.parameters.distance)
    }

    @Test fun rejectsMissingParametersAndUnknownAction() {
        assertThrows(IllegalArgumentException::class.java) { FlightIntentParser.parse("{\"flightType\":\"takeoff\"}") }
        assertThrows(IllegalArgumentException::class.java) { FlightIntentParser.parse("{\"flightType\":\"magic\",\"parameters\":{}}") }
    }

    @Test fun rejectsTextOutsideJsonAndMissingSpeed() {
        assertThrows(IllegalArgumentException::class.java) { FlightIntentParser.parse("好的，{\"flightType\":\"land\",\"parameters\":{}}") }
        assertThrows(IllegalArgumentException::class.java) { FlightIntentParser.parse("{\"flightType\":\"fly_to\",\"parameters\":{\"referenceFrame\":\"body\",\"direction\":\"forward\",\"distance\":10}}") }
    }

    @Test fun rejectsUnexpectedJsonFields() {
        assertThrows(IllegalArgumentException::class.java) {
            FlightIntentParser.parse("{\"flightType\":\"land\",\"parameters\":{\"secretExtra\":1}}")
        }
    }

    @Test fun rejectsTypeCoercionAndCompoundCommand() {
        assertThrows(IllegalArgumentException::class.java) {
            FlightIntentParser.parse("{\"flightType\":\"fly_to\",\"parameters\":{\"referenceFrame\":\"body\",\"direction\":\"forward\",\"distance\":\"10\",\"speed\":2}}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FlightIntentParser.parse("{\"flightType\":\"unknown\",\"parameters\":{\"reason\":\"请拆分起飞和升高\"}}")
        }
    }
}
