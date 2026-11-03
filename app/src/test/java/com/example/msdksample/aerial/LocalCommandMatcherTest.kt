package com.example.msdksample.aerial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCommandMatcherTest {
    @Test fun stopRecordingDoesNotMatchHover() {
        assertEquals("停止录像", LocalCommandMatcher.match("停止录像"))
        assertEquals("停止录像", LocalCommandMatcher.match("停止录制"))
    }

    @Test fun negatedOrMultipleActionsAreRejected() {
        assertNull(LocalCommandMatcher.match("不要起飞"))
        assertNull(LocalCommandMatcher.match("先起飞再拍照"))
        assertNull(LocalCommandMatcher.match(""))
    }

    @Test fun supportedSingleActionsAreMatched() {
        assertEquals("悬停", LocalCommandMatcher.match("悬停"))
        assertEquals("拍照", LocalCommandMatcher.match("拍一张"))
        assertEquals("起飞", LocalCommandMatcher.match("升空"))
        assertEquals("降落", LocalCommandMatcher.match("着陆"))
        assertEquals("返航", LocalCommandMatcher.match("回家"))
        assertEquals("录像", LocalCommandMatcher.match("开始录像"))
    }
}
