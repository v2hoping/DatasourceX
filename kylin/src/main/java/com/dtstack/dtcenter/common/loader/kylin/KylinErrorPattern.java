package com.dtstack.dtcenter.common.loader.kylin;

import com.dtstack.dtcenter.common.loader.common.exception.AbsErrorPattern;
import com.dtstack.dtcenter.common.loader.common.exception.ConnErrorCode;

import java.util.regex.Pattern;

/**
 *
 * @author ：wangchuan
 * date：Created in 下午1:46 2020/11/6
 * company: www.dtstack.com
 */
public class KylinErrorPattern extends AbsErrorPattern {

    private static final Pattern USERNAME_PASSWORD_ERROR = Pattern.compile("(?i)lacks\\s*valid\\s*authentication\\s*credentials");
    static {
        PATTERN_MAP.put(ConnErrorCode.USERNAME_PASSWORD_ERROR.getCode(), USERNAME_PASSWORD_ERROR);
    }
}