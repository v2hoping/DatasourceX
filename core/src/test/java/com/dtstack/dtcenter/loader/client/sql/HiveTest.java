package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.common.enums.DataSourceClientType;
import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.loader.client.AbsClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.ColumnMetaDTO;
import com.dtstack.dtcenter.loader.dto.SourceDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.enums.ClientType;
import org.junit.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 00:13 2020/2/29
 * @Description：Hive 测试
 */
public class HiveTest {
    private static final AbsClientCache clientCache = ClientType.DATA_SOURCE_CLIENT.getClientCache();

    SourceDTO source = SourceDTO.builder()
            .url("jdbc:hive2://cdh-impala2:10000/ceshis_pri")
            .username("root")
            .password("abc123")
            .build();

    @Test
    public void getCon() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.HIVE.getPluginName());
        Connection con = client.getCon(source);
        con.createStatement().close();
        con.close();
    }

    @Test
    public void testCon() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.HIVE.getPluginName());
        Boolean isConnected = client.testCon(source);
        if (Boolean.FALSE.equals(isConnected)) {
            throw new DtCenterDefException("连接异常");
        }
    }

    @Test
    public void executeQuery() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.HIVE.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("show tables").build();
        List<Map<String, Object>> mapList = client.executeQuery(source, queryDTO);
        System.out.println(mapList.size());
    }

    @Test
    public void executeSqlWithoutResultSet() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.HIVE.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("show tables").build();
        client.executeSqlWithoutResultSet(source, queryDTO);
    }

    @Test
    public void getTableList() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.HIVE.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().build();
        List<String> tableList = client.getTableList(source, queryDTO);
        System.out.println(tableList.size());
    }

    @Test
    public void getColumnClassInfo() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.HIVE.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("nanqi200228").build();
        List<String> columnClassInfo = client.getColumnClassInfo(source, queryDTO);
        System.out.println(columnClassInfo.size());
    }

    @Test
    public void getColumnMetaData() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.HIVE.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("nanqi200228").build();
        List<ColumnMetaDTO> columnMetaData = client.getColumnMetaData(source, queryDTO);
        System.out.println(columnMetaData.size());
    }

    @Test
    public void getTableMetaComment() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.HIVE.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("nanqi200228").build();
        String metaComment = client.getTableMetaComment(source, queryDTO);
        System.out.println(metaComment);
    }
}
