package com.example.skybackend.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class KeywordIntentMatcher {

    private static final Map<String, String> KEYWORDS = new LinkedHashMap<>();

    static {
        add("return_home", "返航", "返家", "回到起飞点", "回家");
        add("takeoff", "起飞", "升空");
        add("land", "降落", "着陆");
        add("hover", "悬停", "停在原地");
        add("photo", "拍照", "拍张照片", "照相");
        add("video", "录像", "录视频", "停止录像");
        add("gimbal_control", "云台");
        add("change_altitude", "高度", "上升", "下降", "爬升");
        add("fly_to", "前进", "后退", "往前", "往后", "往左", "往右", "左移", "右移");
        add("rotate", "旋转", "左转", "右转", "转向");
        add("orbit", "环绕", "绕着");
        add("waypoint", "航线", "航点");
    }

    public String suggest(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String normalized = normalize(text);
        Set<String> candidates = new LinkedHashSet<>();
        KEYWORDS.forEach((keyword, intent) -> {
            String normalizedKeyword = normalize(keyword);
            boolean shadowedByMoreSpecificPhrase = KEYWORDS.entrySet().stream().anyMatch(other -> {
                String moreSpecificKeyword = normalize(other.getKey());
                return !other.getValue().equals(intent)
                        && moreSpecificKeyword.length() > normalizedKeyword.length()
                        && moreSpecificKeyword.contains(normalizedKeyword)
                        && normalized.contains(moreSpecificKeyword);
            });
            if (normalized.contains(normalizedKeyword) && !shadowedByMoreSpecificPhrase) {
                candidates.add(intent);
            }
        });
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private static String normalize(String text) {
        return text.toLowerCase().replaceAll("[\\s，。！？、,.!?；;：:]", "");
    }

    private static void add(String intent, String... keywords) {
        for (String keyword : keywords) {
            KEYWORDS.put(keyword, intent);
        }
    }
}
