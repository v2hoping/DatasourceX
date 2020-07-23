package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.common.enums.DataSourceClientType;
import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.loader.IDownloader;
import com.dtstack.dtcenter.loader.client.AbsClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.dto.ColumnMetaDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.ClickHouseSourceDTO;
import com.dtstack.dtcenter.loader.enums.ClientType;
import org.junit.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 15:47 2020/2/28
 * @Description：ClickHouse 测试
 */
public class ClickHouseTest {
    private static final AbsClientCache clientCache = ClientType.DATA_SOURCE_CLIENT.getClientCache();

    ClickHouseSourceDTO source = ClickHouseSourceDTO.builder()
            .url("jdbc:clickhouse://172.16.10.168:8123/mqTest")
            .username("dtstack")
            .password("abc123")
            .schema("mqTest")
            .build();

    @Test
    public void getCon() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        Connection con = client.getCon(source);
        con.createStatement().close();
        con.close();
    }

    @Test
    public void testCon() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        Boolean isConnected = client.testCon(source);
        if (Boolean.FALSE.equals(isConnected)) {
            throw new DtCenterDefException("连接异常");
        }
    }

    @Test
    public void executeQuery() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("show tables").build();
        List<Map<String, Object>> mapList = client.executeQuery(source, queryDTO);
        System.out.println(mapList.size());
    }

    @Test
    public void executeSqlWithoutResultSet() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("show tables").build();
        client.executeSqlWithoutResultSet(source, queryDTO);
    }

    @Test
    public void getTableList() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        List<String> tableList = client.getTableList(source, null);
        System.out.println(tableList);
    }

    @Test
    public void getColumnClassInfo() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("mqresult2").build();
        List<String> columnClassInfo = client.getColumnClassInfo(source, queryDTO);
        System.out.println(columnClassInfo.size());
    }

    @Test
    public void getColumnMetaData() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("mqresult2").build();
        List<ColumnMetaDTO> columnMetaData = client.getColumnMetaData(source, queryDTO);
        System.out.println(columnMetaData.size());
    }

    @Test
    public void getDownloader() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().sql("select * from mqresult2").build();
        IDownloader downloader = client.getDownloader(source, queryDTO);
        System.out.println(downloader.getMetaInfo());
        while (!downloader.reachedEnd()){
            List<List<String>> o = (List<List<String>>)downloader.readNext();
            for (List list:o){
                System.out.println(list);
            }
        }

    }

    @Test
    public void testGetPreview() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("sidetest").previewNum(1).build();
        List preview = client.getPreview(source, queryDTO);
        System.out.println(preview);
    }

    @Test
    public void getAllDatabases() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().build();
        System.out.println(client.getAllDatabases(source,queryDTO));
    }

    @Test
    public void getCreateTableSql() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("cust").build();
        System.out.println(client.getCreateTableSql(source,queryDTO));
    }

    @Test
    public void getPartitionColumn() throws Exception {
        IClient client = clientCache.getClient(DataSourceClientType.Clickhouse.getPluginName());
        SqlQueryDTO queryDTO = SqlQueryDTO.builder().tableName("cust").build();
        System.out.println(client.getPartitionColumn(source,queryDTO));
    }
}
