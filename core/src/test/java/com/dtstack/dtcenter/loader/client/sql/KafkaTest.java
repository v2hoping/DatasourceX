package com.dtstack.dtcenter.loader.client.sql;

import com.dtstack.dtcenter.loader.client.ClientCache;
import com.dtstack.dtcenter.loader.client.IClient;
import com.dtstack.dtcenter.loader.client.IKafka;
import com.dtstack.dtcenter.loader.dto.KafkaOffsetDTO;
import com.dtstack.dtcenter.loader.dto.KafkaPartitionDTO;
import com.dtstack.dtcenter.loader.dto.KafkaTopicDTO;
import com.dtstack.dtcenter.loader.dto.SqlQueryDTO;
import com.dtstack.dtcenter.loader.dto.source.KafkaSourceDTO;
import com.dtstack.dtcenter.loader.exception.DtLoaderException;
import com.dtstack.dtcenter.loader.source.DataSourceType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.kafka.common.requests.MetadataResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * @company: www.dtstack.com
 * @Author ：Nanqi
 * @Date ：Created in 13:04 2020/2/29
 * @Description：Kafka 测试类
 */
@Slf4j
public class KafkaTest {
    KafkaSourceDTO source = KafkaSourceDTO.builder()
            .url("172.16.100.112:2181/kafka")
            .build();

    @Before
    public void setUp() {
        createTopic();
    }

    @Test
    public void testConForKafka() {
        IKafka client = ClientCache.getKafka(DataSourceType.KAFKA_09.getVal());
        Boolean isConnected = client.testCon(source);
        if (Boolean.FALSE.equals(isConnected)) {
            throw new DtLoaderException("连接异常");
        }
    }

    @Test
    public void testConForClient() {
        IClient client = ClientCache.getClient(DataSourceType.KAFKA_09.getVal());
        Boolean isConnected = client.testCon(source);
        if (Boolean.FALSE.equals(isConnected)) {
            throw new DtLoaderException("连接异常");
        }
    }

    @Test
    public void getAllBrokersAddress() {
        IKafka client = ClientCache.getKafka(DataSourceType.KAFKA_09.getVal());
        String brokersAddress = client.getAllBrokersAddress(source);
        assert (null != brokersAddress);
    }

    @Test
    public void getTopicList() {
        IKafka client = ClientCache.getKafka(DataSourceType.KAFKA_09.getVal());
        List<String> topicList = client.getTopicList(source);
        assert (topicList != null);
        System.out.println(topicList);
    }

    @Test
    public void createTopic() {
        try {
            IKafka client = ClientCache.getKafka(DataSourceType.KAFKA_09.getVal());
            KafkaTopicDTO topicDTO = KafkaTopicDTO.builder().partitions(1).replicationFactor((short) 1).topicName(
                    "nanqi").build();
            Boolean clientTopic = client.createTopic(source, topicDTO);
            assert (Boolean.TRUE.equals(clientTopic));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }

    @Test
    public void getAllPartitions() {
        // 测试的时候需要引进 kafka 包
        IKafka client = ClientCache.getKafka(DataSourceType.KAFKA_09.getVal());
        List<MetadataResponse.PartitionMetadata> allPartitions = client.getAllPartitions(source, "nanqi");
        System.out.println(allPartitions.size());
    }

    @Test
    public void getOffset() {
        IKafka client = ClientCache.getKafka(DataSourceType.KAFKA_09.getVal());
        List<KafkaOffsetDTO> offset = client.getOffset(source, "nanqi");
        assert (offset != null);
    }

    @Test
    public void testPollView(){
        IKafka client = ClientCache.getKafka(DataSourceType.KAFKA_09.getVal());
        SqlQueryDTO sqlQueryDTO = SqlQueryDTO.builder().tableName("nanqi").build();
        List<List<Object>> results = client.getPreview(source,sqlQueryDTO);
        System.out.println(results);
    }

    @Test
    public void testPollViewLatest(){
        IKafka client = ClientCache.getKafka(DataSourceType.KAFKA_09.getVal());
        SqlQueryDTO sqlQueryDTO = SqlQueryDTO.builder().tableName("nanqi").build();
        List<List<Object>> results = client.getPreview(source, sqlQueryDTO, "latest");
        System.out.println(results);
    }

    @Test
    public void testTopicPartitions() {
        IKafka client = ClientCache.getKafka(DataSourceType.KAFKA_09.getVal());
        List<KafkaPartitionDTO> partitionDTOS = client.getTopicPartitions(source, "partiton_test");
        Assert.assertTrue(CollectionUtils.isNotEmpty(partitionDTOS));
        System.out.println(partitionDTOS);
    }
}
