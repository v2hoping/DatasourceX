package com.dtstack.dtcenter.common.loader.inceptor;

import com.dtstack.dtcenter.common.loader.common.exception.AbsErrorPattern;
import com.dtstack.dtcenter.common.loader.common.exception.ConnErrorCode;

import java.util.regex.Pattern;

/**
 *
 * @author ：wangchuan
 * date：Created in 下午1:46 2020/11/6
 * company: www.dtstack.com
 */
public class InceptorErrorPattern extends AbsErrorPattern {

    private static final Pattern DB_NOT_EXISTS = Pattern.compile("(?i)Database\\s+'(?<database>(.*))'\\s+not\\s+found");

    private static final Pattern DB_PERMISSION_ERROR = Pattern.compile("(?i)Permission\\s*denied");

    static {
        PATTERN_MAP.put(ConnErrorCode.DB_NOT_EXISTS.getCode(), DB_NOT_EXISTS);
        PATTERN_MAP.put(ConnErrorCode.DB_PERMISSION_ERROR.getCode(), DB_PERMISSION_ERROR);
    }

}