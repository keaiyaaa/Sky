package com.tianshu.backend.settings;

import com.tianshu.backend.common.ApiFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import static com.tianshu.backend.settings.SettingsModels.*;

public final class SettingsRules {
    private SettingsRules() {}
    public static Values normalize(Values values) {
        if(values==null) throw ApiFailure.invalid("settings","必须提供全部设置字段");
        String auto=code(values.autoSyncRecords(),"autoSyncRecords",Set.of("0","1"));
        String low=code(values.lowBatteryReturn(),"lowBatteryReturn",Set.of("0","1"));
        String fail=code(values.failsafeAction(),"failsafeAction",Set.of("0","1","2"));
        String obstacle=code(values.obstacleAction(),"obstacleAction",Set.of("0","1"));
        String video=code(values.videoQuality(),"videoQuality",Set.of("1080p"));
        String image=code(values.imageQuality(),"imageQuality",Set.of("high"));
        String beginner=code(values.beginnerMode(),"beginnerMode",Set.of("0","1"));
        if(values.controlSensitivity()==null || values.controlSensitivity()<1 || values.controlSensitivity()>10)
            throw ApiFailure.invalid("settings.controlSensitivity","灵敏度为1至10档");
        String height=values.returnAltitudeM();
        if(height!=null) {
            height=height.strip();
            if(height.length()>16 || !height.matches("[0-9]+(\\.[0-9]{1,2})?")) throw invalidHeight();
            BigDecimal decimal=new BigDecimal(height);
            if(decimal.compareTo(new BigDecimal("20"))<0 || decimal.compareTo(new BigDecimal("400"))>0) throw invalidHeight();
            if(beginner.equals("1") && decimal.compareTo(new BigDecimal("30"))>0)
                throw ApiFailure.invalid("settings.returnAltitudeM","新手模式的本地高度选项不得超过30米");
            height=decimal.setScale(2,RoundingMode.UNNECESSARY).toPlainString();
        }
        return new Values(auto,low,height,fail,obstacle,values.controlSensitivity(),video,image,beginner);
    }
    private static String code(String value,String field,Set<String> allowed) {
        if(value==null || !allowed.contains(value)) throw ApiFailure.invalid("settings."+field,"设置编码无效");
        return value;
    }
    private static ApiFailure invalidHeight() {
        return ApiFailure.invalid("settings.returnAltitudeM","高度须为20至400米的十进制字符串、最多两位小数，或null");
    }
}
