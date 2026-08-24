package ai.deepseek.harness.dsh

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshResponseParsingTest {

  private fun obj(json: String) = Json.parseToJsonElement(json).jsonObject

  @Test
  fun parsesModelGroups() {
    val v = obj(
      """{
        "current": {"provider": "p1", "model": "m2"},
        "routable": true,
        "groups": [
          {"id": "p1", "name": "Provider One", "models": [
            {"id": "m1", "name": "Model One"},
            {"id": "m2", "name": "Model Two", "description": "desc"}
          ]},
          {"id": "p2", "name": null, "models": [{"id": "m3"}]}
        ],
        "failures": [{"message": "boom"}, "not-an-object"]
      }""",
    )
    val snap = parseModelsSnapshot(v)
    assertEquals("p1", snap.currentProvider)
    assertEquals("m2", snap.currentModel)
    assertTrue(snap.routable)
    assertEquals(3, snap.options.size)
    assertEquals("p2", snap.options[2].providerName) // name 为空回退 providerId
    assertEquals(listOf("boom"), snap.failures)     // 非对象条目跳过而非 CCE
  }

  @Test
  fun toleratesMalformedGroupEntries() {
    val v = obj(
      """{
        "groups": [
          "not-an-object",
          {"id": "p1", "models": ["bad", {"id": "m1"}]}
        ]
      }""",
    )
    val snap = parseModelsSnapshot(v)
    assertEquals(1, snap.options.size)
    assertEquals("m1", snap.options[0].modelId)
    assertTrue(snap.currentModel.isEmpty())
  }

  @Test
  fun routableDefaultsTrueWhenAbsent() {
    val snap = parseModelsSnapshot(obj("{}"))
    assertTrue(snap.routable)
    assertFalse(snap.options.isNotEmpty())
  }
}
