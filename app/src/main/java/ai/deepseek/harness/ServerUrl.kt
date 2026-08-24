package ai.deepseek.harness

import java.net.URI

/**
 * 校验并规范化用户输入的 DSH 服务器地址。
 * 仅接受 https://（凭据安全基线）；返回去掉结尾斜杠的规范化地址，非法输入返回 null。
 * 纯 JVM 实现（java.net.URI），便于单元测试直接覆盖。
 */
internal fun normalizeServerUrl(rawInput: String): String? {
  val trimmed = rawInput.trim().removeSuffix("/")
  if (!trimmed.startsWith("https://")) return null
  val uri = try {
    URI(trimmed)
  } catch (_: Exception) {
    return null
  }
  val host = uri.host?.takeIf { it.isNotEmpty() } ?: return null
  // 拒绝内嵌 userinfo 等异常形态
  if (uri.userInfo != null) return null
  val port = if (uri.port > 0 && uri.port != 443) ":" + uri.port else ""
  val path = uri.path.orEmpty()
  // path 需以 / 开头或为空；URI 解析已保证，但防御性处理一次
  val safePath = when {
    path.isEmpty() -> ""
    path.startsWith("/") -> path
    else -> "/" + path
  }
  return "https://" + host.lowercase() + port + safePath
}
