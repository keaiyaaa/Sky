package com.tianshu.backend.common;

public final class FieldRules {
    private FieldRules() {}

    public static String text(String value, String field, int max, boolean required) {
        if (value == null) throw ApiFailure.invalid(field, "字段不能为空");
        String normalized = value.strip();
        if ((required && normalized.isEmpty()) || normalized.codePointCount(0, normalized.length()) > max
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiFailure.invalid(field, required ? "长度或内容无效" : "字段过长或包含控制字符");
        }
        return normalized;
    }

    public static void version(Long version, boolean creating) {
        if (version == null || (creating ? version != 0 : version < 1)) {
            throw ApiFailure.invalid("expectedVersion", creating ? "新建版本必须为0" : "必须提交当前版本");
        }
    }
}
