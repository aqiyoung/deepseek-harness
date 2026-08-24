package ai.deepseek.harness.dsh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DshPluginManifestParsingTest {

  @Test
  fun extractsUserFacingPluginsFromIndexHtml() {
    val html = "<script>__DSH_PLUGINS=[{" +
      "\"id\":\"@deepseek-ai/dsh-client-runtime\",\"url\":\"/plugins/@deepseek-ai/dsh-client-runtime/client.js?rev=aba\",\"rev\":\"aba\"}," +
      "{\"id\":\"dsh-web-ui-mobile\",\"url\":\"/plugins/dsh-web-ui-mobile/client.js?rev=8701\",\"rev\":\"8701\"}," +
      "{\"id\":\"dsh-wechat-push\",\"url\":\"/plugins/dsh-wechat-push/client.js?rev=a5f9\",\"rev\":\"a5f9\"}]" +
      "</script>"
    val entries = parsePluginManifest(html)
    // 排序大小写不敏感："dsh-web..." < "dsh-wec..."
    assertEquals(listOf("dsh-web-ui-mobile", "dsh-wechat-push"), entries.map { it.name })
    assertTrue(entries.all { it.enabled && it.phase == "installed" })
  }

  @Test
  fun skipsInfraAndNonPluginEntries() {
    val html = "{" +
      "\"id\":\"@deepseek-ai/dsh-client-modules\",\"url\":\"/plugins/@deepseek-ai/dsh-client-modules/client.js?rev=x\"," +
      "\"id\":\"@deepseek-ai/dsh-api-gateway\",\"url\":\"/plugins/@deepseek-ai/dsh-api-gateway/client.js?rev=y\"," +
      "\"id\":\"unrelated\",\"url\":\"/static/unrelated.js\"}"
    assertEquals(emptyList<String>(), parsePluginManifest(html).map { it.name })
  }

  @Test
  fun dedupesByNameAndSortsCaseInsensitive() {
    val html = "{\"id\":\"zeta-plugin\",\"url\":\"/plugins/zeta-plugin/client.js?rev=1\"}," +
      "{\"id\":\"Alpha-plugin\",\"url\":\"/plugins/alpha/client.js?rev=2\"}"
    val names = parsePluginManifest(html).map { it.name }
    assertEquals(listOf("Alpha-plugin", "zeta-plugin"), names)
  }
}
