package com.dtstack.dtcenter.common.loader.spark;

import com.dtstack.dtcenter.common.loader.common.DtClassConsistent;
import com.dtstack.dtcenter.common.loader.common.utils.DBUtil;
import com.dtstack.dtcenter.common.loader.rdbms.ConnFactory;
import com.dtstack.dtcenter.common.loader.spark.util.SparkKerberosLoginUtil;
import com.dtstack.dtcenter.loader.dto.source.ISourceDTO;
import com.dtstack.dtcenter.loader.dto.source.SparkSourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataBaseType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.security.PrivilegedAction;
import java.sql.Connection;
import java.util.regex.Matcher;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 17:07 2020/1/7
 * @Description：Spark 连接池工厂
 */
@Slf4j
public class SparkConnFactory extends ConnFactory {
    public SparkConnFactory() {
        this.driverName = DataBaseType.Spark.getDriverClassName();
        this.errorPattern = new SparkErrorPattern();
    }

    @Override
    public Connection getConn(ISourceDTO iSource) throws Exception {
        init();
        SparkSourceDTO sparkSourceDTO = (SparkSourceDTO) iSource;

        Connection connection;
        if (MapUtils.isNotEmpty(sparkSourceDTO.getKerberosConfig())) {
            connection = SparkKerberosLoginUtil.loginKerberosWithUGI(sparkSourceDTO.getUrl(), sparkSourceDTO.getKerberosConfig()).doAs(
                    (PrivilegedAction<Connection>) () -> {
                        try {
                            return super.getConn(sparkSourceDTO);
                        } catch (Exception e) {
                            throw new DtLoaderException("getHiveConnection error : " + e.getMessage(), e);
                        }
                    }
            );
        } else {
            connection = super.getConn(sparkSourceDTO);
        }

        Matcher matcher = DtClassConsistent.PatternConsistent.HIVE_JDBC_PATTERN.matcher(sparkSourceDTO.getUrl());
        String db = null;
        if (!matcher.find()) {
            db = matcher.group(DtClassConsistent.PublicConsistent.DB_KEY);
        }
        db = StringUtils.isBlank(sparkSourceDTO.getSchema()) ? db : sparkSourceDTO.getSchema();
        if (StringUtils.isNotEmpty(db)) {
            DBUtil.executeSqlWithoutResultSet(connection, String.format(DtClassConsistent.PublicConsistent.USE_DB,
                    db), false);
        }
        return connection;
    }
}
