package com.dtstack.dtcenter.common.loader.rdbms.dm;

import com.dtstack.dtcenter.common.enums.DataSourceType;
import com.dtstack.dtcenter.common.exception.DtCenterDefException;
import com.dtstack.dtcenter.common.loader.rdbms.common.AbsRdbmsClient;
import com.dtstack.dtcenter.common.loader.rdbms.common.ConnFactory;
import com.dtstack.dtcenter.loader.dto.SourceDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.utils.DBUtil;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 11:19 2020/4/17
 * @Description：达梦客户端
 */
public class DmClient extends AbsRdbmsClient {
    private static final String ORACLE_ALL_TABLES_SQL = "SELECT TABLE_NAME FROM USER_TABLES UNION SELECT GRANTOR||'" +
            ".'||'\"'||TABLE_NAME||'\"' FROM ALL_TAB_PRIVS WHERE grantee = (SELECT USERNAME FROM user_users WHERE " +
            "ROWNUM = 1) ";
    private static final String ORACLE_WITH_VIEWS_SQL = "UNION SELECT VIEW_NAME FROM USER_VIEWS ";

    @Override
    protected ConnFactory getConnFactory() {
        return new DmConnFactory();
    }

    @Override
    protected DataSourceType getSourceType() {
        return DataSourceType.DMDB;
    }

    @Override
    public List<String> getTableList(SourceDTO source, SqlQueryDTO queryDTO) throws Exception {
        Integer clearStatus = beforeQuery(source, queryDTO, false);

        Statement statement = null;
        ResultSet rs = null;
        List<String> tableList = new ArrayList<>();
        try {
            String sql = queryDTO != null && queryDTO.getView() ? ORACLE_ALL_TABLES_SQL + ORACLE_WITH_VIEWS_SQL :
                    ORACLE_ALL_TABLES_SQL;
            statement = source.getConnection().createStatement();
            rs = statement.executeQuery(sql);
            int columnSize = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                tableList.add(rs.getString(1));
            }
        } catch (Exception e) {
            throw new DtCenterDefException("获取表异常", e);
        } finally {
            DBUtil.closeDBResources(rs, statement, source.clearAfterGetConnection(clearStatus));
        }
        return tableList;
    }
}
