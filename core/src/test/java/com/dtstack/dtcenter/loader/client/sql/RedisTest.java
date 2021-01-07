package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.loader.client.ClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.RedisSourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import org.junit.Test;

import java.util.List;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 14:03 2020/2/29
 * @Description：Redis 测试
 */
public class RedisTest {
    RedisSourceDTO source = RedisSourceDTO.builder()
            .hostPort("172.16.101.249:6379")
            .password("DT@Stack#123")
            .schema("5")
            .build();

    @Test
    public void testCon() throws Exception {
        IClient client = ClientCache.getClient(DataSourceType.REDIS.getVal());
        Boolean isConnected = client.testCon(source);
        if (Boolean.FALSE.equals(isConnected)) {
            throw new DtLoaderException("连接异常");
        }
    }

    @Test
    public void preview() throws Exception {
        IClient client = ClientCache.getClient(DataSourceType.REDIS.getVal());
        List test = client.getPreview(source, SqlQueryDTO.builder().previewNum(5).tableName("wangchuan_test").build());
        System.out.println(test);
    }
}
