package vn.vedax.hardwaretest.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SemanticVersionTest {
    @Test fun newerPatchWins() {
        assertEquals(1, SemanticVersion.compare("v0.4.0", "0.3.3"))
    }

    @Test fun missingPatchIsZero() {
        assertEquals(0, SemanticVersion.compare("v1.2", "1.2.0"))
    }

    @Test fun olderMajorLoses() {
        assertEquals(-1, SemanticVersion.compare("0.9.99", "1.0.0"))
    }

    @Test fun rejectsNonNumericTag() {
        assertThrows(IllegalArgumentException::class.java) {
            SemanticVersion.compare("latest", "0.4.0")
        }
    }
}
