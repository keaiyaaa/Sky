package com.example.msdksample.aerial

/** Matches one supported offline command. Ambiguous and negated phrases are rejected. */
object LocalCommandMatcher {
    fun match(command: String): String? {
        val text = command.trim().lowercase()
        if (text.isEmpty() || listOf("不要", "别", "不需要", "先", "然后", "再").any(text::contains)) return null

        return when {
            listOf("停止录像", "停止录制", "结束录像", "不录了").any(text::contains) -> "停止录像"
            listOf("录像", "录制").any(text::contains) -> "录像"
            listOf("返航", "回来", "回家").any(text::contains) -> "返航"
            listOf("悬停", "不动").any(text::contains) -> "悬停"
            listOf("拍照", "拍一张", "照相").any(text::contains) -> "拍照"
            listOf("起飞", "升空", "飞起来").any(text::contains) -> "起飞"
            listOf("降落", "着陆").any(text::contains) -> "降落"
            else -> null
        }
    }
}
