package com.example

import com.example.util.SecurityUtil
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun pinHashingAndVerification_worksCorrectly() {
    val rawPin = "5678"
    val hash = SecurityUtil.hashPin(rawPin)

    assertTrue(SecurityUtil.verifyPin("5678", hash))
    assertFalse(SecurityUtil.verifyPin("1234", hash))
    assertFalse(SecurityUtil.verifyPin("5679", hash))
  }
}

