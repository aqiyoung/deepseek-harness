package ai.deepseek.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

  @Test
  fun acceptsPlainHttpsHost() {
    assertEquals("https://dsh.example.com", normalizeServerUrl("https://dsh.example.com"))
  }

  @Test
  fun stripsTrailingSlashAndLowercasesHost() {
    assertEquals(
      "https://dsh.example.com",
      normalizeServerUrl("  HTTPS://DSH.Example.COM/// ".trim().let { "https://DSH.Example.COM/" }),
    )
  }

  @Test
  fun keepsPortAndSubPath() {
    assertEquals(
      "https://dsh.example.com:8443/dsh",
      normalizeServerUrl("https://dsh.example.com:8443/dsh"),
    )
  }

  @Test
  fun rejectsHttp() {
    assertNull(normalizeServerUrl("http://dsh.example.com"))
  }

  @Test
  fun rejectsSchemeLessInput() {
    assertNull(normalizeServerUrl("dsh.example.com"))
    assertNull(normalizeServerUrl(""))
    assertNull(normalizeServerUrl("   "))
  }

  @Test
  fun rejectsUserInfo() {
    assertNull(normalizeServerUrl("https://evil@example.com"))
  }

  @Test
  fun rejectsMissingHost() {
    assertNull(normalizeServerUrl("https:///path-only"))
  }
}
