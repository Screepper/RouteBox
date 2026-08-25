
package com.routebox.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.routebox.entity.index.*;
import com.routebox.entity.page.PageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * @Description
 * @Date 2025/11/20
**/
@Service
@Slf4j
public class EsService {
    private final ElasticsearchClient esClient;

    private static final int DEFAULT_MAX_SIZE = 9999;

    public EsService(ElasticsearchClient esClient) {
        this.esClient = esClient;
    }

    /**
     * @Description Get index name by log type
     * @Date 2025/7/3
     **/
    private String indexName(IndexBase indexBase) {
        if (indexBase instanceof AttackLog) return "attack_log";
        if (indexBase instanceof BgpLog) return "bgp_log";
        if (indexBase instanceof BgpUpdateLog) return "bgp_update_log";
        if (indexBase instanceof RouterLog) return "router_log";
        if (indexBase instanceof PingLog) return "ping_log";
        if (indexBase instanceof SystemNetworkLog) return "system_network_log";
        if (indexBase instanceof TopologyLog) return "topology_log";
        if (indexBase instanceof DetectResultLog) return "detect_result_log";

        throw new RuntimeException("Unsupported log type: " + indexBase.getClass());
    }

    /**
     * @Description Get index name by log class
     **/
    private String indexName(Class<?> clazz) {
        if (clazz.equals(AttackLog.class)) return "attack_log";
        if (clazz.equals(BgpLog.class)) return "bgp_log";
        if (clazz.equals(BgpUpdateLog.class)) return "bgp_update_log";
        if (clazz.equals(RouterLog.class)) return "router_log";
        if (clazz.equals(PingLog.class)) return "ping_log";
        if (clazz.equals(SystemNetworkLog.class)) return "system_network_log";
        if (clazz.equals(TopologyLog.class)) return "topology_log";
        if (clazz.equals(DetectResultLog.class)) return "detect_result_log";

        throw new RuntimeException("Unsupported log type: " + clazz);
    }

    /**
     * @Description Save 1 log
     * @Date 2025/7/3
     **/
    public void save(IndexBase baseLog) {
        try {
            IndexResponse response = esClient.index(b -> b
                    .index(indexName(baseLog))
                    .id(baseLog.getLogId())
                    .document(baseLog)
            );

            if (!response.result().equals(Result.Created)) {
                log.error("Single insert failed: {}", response.result());
            }
        } catch (IOException e) {
            log.error("Single insert error: {}", e.getMessage());
        }
    }

    /**
     * @Description Save batch logs
     * @Date 2025/7/3
     **/
    public void bulkSave(List<IndexBase> logs) {
        if (logs.isEmpty()) return;

        try {
            BulkResponse response = esClient.bulk(b -> b
                    .operations(logs
                            .stream()
                            .map(log -> new BulkOperation
                                    .Builder()
                                    .index(i -> i
                                            .index(indexName(log))
                                            .id(log.getLogId())
                                            .document(log)
                                    ).build()
                            ).toList()
                    )
            );

            if (response.errors()) {
                log.error("Bulk insert failed");
            }
        } catch (IOException e) {
            log.error("Bulk insert error: {}", e.getMessage());
        }
    }

    /**
     * @Description update 1
     * @Date 2025/7/3
     **/
    public void update(IndexBase idx) {
        try {
            esClient.update(
                    UpdateRequest.of(b -> b
                            .index(indexName(idx))
                            .id(idx.getLogId())
                            .doc(idx)
                    ), idx.getClass()
            );
        } catch (IOException e) {
            log.error("update error: {}", e.getMessage());
        }
    }

    /**
     * @Description delete 1
     * @Date 2025/7/3
     **/
    public <T extends IndexBase> void delete(String logId, Class<T> clazz) {
        try {
            esClient.delete(b -> b
                    .index(indexName(clazz))
                    .id(logId));
        } catch (IOException e) {
            log.error("delete error: {}", e.getMessage());
        }
    }


    /**
     * @Description Search
     * @Date 2025/7/3
     **/
    public <T extends IndexBase> List<T> search(Class<T> clazz, boolean ascOrder, int... expectSize) {
        int size = expectSize.length > 0 ? (Math.min(DEFAULT_MAX_SIZE, Math.max(1, expectSize[0]))) : DEFAULT_MAX_SIZE;

        try {
            SearchResponse<T> response = esClient.search(b -> b
                    .index(indexName(clazz))
                    .size(size)
                    .timeout("120s")
                    .sort(s -> s
                            .field(f -> f
                                    .field("timestamp")
                                    .order(ascOrder ? SortOrder.Asc : SortOrder.Desc)
                            )
                    ), clazz
            );

            return response.hits().hits().stream().map(Hit::source).toList();
        } catch (IOException e) {
            log.error("Search error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * @Description Search with order
     * @Date 2025/7/3
     **/
    public <T extends IndexBase> List<T> searchByOrder(Class<T> clazz, boolean ascOrder) {
        try {
            SearchResponse<T> response = esClient.search(b -> b
                    .index(indexName(clazz))
                    .size(DEFAULT_MAX_SIZE)
                    .timeout("120s")
                    .sort(s -> s
                            .field(f -> f
                                    .field("timestamp")
                                    .order(ascOrder ? SortOrder.Asc : SortOrder.Desc)
                            )
                    ), clazz
            );

            return response.hits().hits().stream().map(Hit::source).toList();
        } catch (IOException e) {
            log.error("Search with order error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * @Description Search by eventId
     * @Date 2025/7/3
     **/
    public <T extends IndexBase> T searchByEventId(String eventId, Class<T> clazz) {
        if (eventId == null) {
            log.error("eventId cannot be null");
            return null;
        }

        Query byEventId = TermQuery.of(q -> q
                .field("eventId")
                .value(eventId)
        )._toQuery();

        try {
            SearchResponse<T> response = esClient.search(b -> b
                    .index(indexName(clazz))
                    .query(q -> q
                            .bool(b1 -> b1
                                    .must(byEventId)
                            )
                    ).size(DEFAULT_MAX_SIZE)
                    .timeout("120s"), clazz
            );

            return response.hits().hits().stream().map(Hit::source).toList().getFirst();
        } catch (IOException e) {
            log.error("Search by eventId error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * @Description Search by time range
     * @Date 2025/7/3
     **/
    public <T extends IndexBase> List<T> searchByTimeRange(Long startTime, Long endTime, Class<T> clazz) {
        if (startTime == null || endTime == null) {
            log.error("Time cannot be null in searchByTime");
            return null;
        }

        if (startTime > endTime) {
            log.error("Start time cannot be greater than end time in searchByTime");
            return null;
        }

        Query timeRange = RangeQuery.of(q -> q
                .term(n -> n
                        .field("timestamp")
                        .gte(String.valueOf(startTime))
                        .lte(String.valueOf(endTime))
                )
        )._toQuery();

        try {
            SearchResponse<T> response = esClient.search(b -> b
                    .index(indexName(clazz))
                    .query(timeRange)
                    .size(DEFAULT_MAX_SIZE)
                    .timeout("120s"), clazz
            );

            return response.hits().hits().stream().map(Hit::source).toList();
        } catch (IOException e) {
            log.error("Search by time range failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * @Description Search by nodeId and time range (close interval)
     * @Date 2025/7/3
     **/
    public <T extends IndexBase> List<T> searchByNodeIdAndTimeRange(Integer nodeId, Long startTime, Long endTime, Class<T> clazz) {
        if (nodeId == null) {
            log.error("NodeId cannot be null in searchByNodeIdAndTime");
            return null;
        }

        if (startTime == null || endTime == null) {
            log.error("Time cannot be null in searchByNodeIdAndTime");
            return null;
        }

        if (startTime > endTime) {
            log.error("Start time cannot be greater than end time in searchByNodeIdAndTime");
            return null;
        }

        Query byNodeId = TermQuery.of(q -> q
                .field("nodeId")
                .value(nodeId)
        )._toQuery();

        Query timeRange = RangeQuery.of(q -> q
                .term(n -> n
                        .field("timestamp")
                        .gte(String.valueOf(startTime))
                        .lte(String.valueOf(endTime))
                )
        )._toQuery();

        try {
            SearchResponse<T> response = esClient.search(b -> b
                    .index(indexName(clazz))
                    .query(q -> q
                            .bool(b1 -> b1
                                    .must(byNodeId)
                                    .must(timeRange)
                            )
                    ).size(DEFAULT_MAX_SIZE)
                    .timeout("120s"), clazz
            );

            return response.hits().hits().stream().map(Hit::source).toList();
        } catch (IOException e) {
            log.error("Search by nodeId and time range error: {}", e.getMessage());
            return null;
        }
    }

//    /**
//     * @Description Scroll search by nodeId and time range
//     * @Date 2026/4/26
//     **/
//    public <T extends IndexBase> List<T> searchByNodeIdAndTimeRange(Integer nodeId, Long startTime, Long endTime, Class<T> clazz) {
//        if (nodeId == null) {
//            log.error("NodeId cannot be null in searchByNodeIdAndTime");
//            return null;
//        }
//        if (startTime == null || endTime == null) {
//            log.error("Time cannot be null in searchByNodeIdAndTime");
//            return null;
//        }
//        if (startTime > endTime) {
//            log.error("Start time cannot be greater than end time in searchByNodeIdAndTime");
//            return null;
//        }
//
//        Query byNodeId = TermQuery.of(q -> q
//                .field("nodeId")
//                .value(nodeId)
//        )._toQuery();
//
//        Query timeRange = RangeQuery.of(q -> q
//                .term(n -> n
//                        .field("timestamp")
//                        .gte(String.valueOf(startTime))
//                        .lte(String.valueOf(endTime))
//                )
//        )._toQuery();
//
//        String scrollId = null;
//
//        try {
//            // ===================== Initialize Scroll Search =====================
//            SearchResponse<T> initialResponse = esClient.search(b -> b
//                            .index(indexName(clazz))
//                            .query(q -> q
//                                    .bool(b1 -> b1
//                                            .must(byNodeId)
//                                            .must(timeRange)
//                                    )
//                            )
//                            .size(DEFAULT_MAX_SIZE)
//                            .scroll(t -> t.time("1m"))
//                            .timeout("120s")
//                    , clazz);
//
//            List<T> resultList = new ArrayList<>(initialResponse.hits().hits().stream().map(Hit::source).toList());
//            // Fetch the scrollId from the initial response to start the scroll loop
//            final String currScrollId = initialResponse.scrollId();
//
//            // ===================== Scroll Search until no more page data =====================
//            while (true) {
//                ScrollResponse<T> scrollResponse = esClient.scroll(ScrollRequest.of(s -> s
//                        .scrollId(currScrollId)
//                        .scroll(t -> t.time("1m"))
//                ), clazz);
//
//                List<T> currentHits = scrollResponse.hits().hits().stream().map(Hit::source).toList();
//                if (currentHits.isEmpty()) break;
//
//                resultList.addAll(currentHits);
//                scrollId = scrollResponse.scrollId();
//            }
//
//            return resultList;
//
//        } catch (IOException e) {
//            log.error("Scroll search by nodeId and time range error: {}", e.getMessage());
//            return null;
//        } finally {
//            // ===================== Clear Scroll Resource =====================
//            if (scrollId != null) {
//                try {
//                    final String currScrollId = scrollId;
//                    esClient.clearScroll(ClearScrollRequest.of(r -> r.scrollId(currScrollId)));
//                    log.info("Scroll resource cleared successfully, scrollId: {}", currScrollId);
//                } catch (IOException ex) {
//                    log.error("Failed to clear scroll resource: {}", ex.getMessage());
//                }
//            }
//        }
//    }


    /**
     * @Description Check if there is an attack event in the time range
     * @Date 2025/7/3
     **/
    public boolean checkAttackInTimeRange(Long startTime, Long endTime) {
        if (startTime == null || endTime == null) {
            log.error("Time cannot be null in checkAttackInTimeRange");
            return false;
        }

        if (startTime > endTime) {
            log.error("Start time cannot be greater than end time in checkAttackInTimeRange");
            return false;
        }

        Query startTimeRange = RangeQuery.of(q -> q
                .term(n -> n
                        .field("timestamp")
                        .gt(String.valueOf(startTime))
                        .lt(String.valueOf(endTime))
                )
        )._toQuery();

        Query endTimeRange = RangeQuery.of(q -> q
                .term(n -> n
                        .field("endTimestamp")
                        .gt(String.valueOf(startTime))
                        .lt(String.valueOf(endTime))
                )
        )._toQuery();

        try {
            SearchResponse<AttackLog> response = esClient.search(b -> b
                    .index("attack_log")
                    .query(q -> q
                            .bool(b1 -> b1
                                    .should(startTimeRange)
                                    .should(endTimeRange)
                                    .minimumShouldMatch("1")
                            )
                    )
                    .size(DEFAULT_MAX_SIZE)
                    .timeout("120s"), AttackLog.class
            );

            return !response.hits().hits().isEmpty();
        } catch (IOException e) {
            log.error("Search attack log by time range error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * @Description Check if there is an attack event in the time range
     * @Date 2025/7/3
     **/
    public String getAttackIdInTimeRange(Long startTime, Long endTime) {
        Query startTimeRange = RangeQuery.of(q -> q
                .term(n -> n
                        .field("timestamp")
                        .gt(String.valueOf(startTime))
                        .lt(String.valueOf(endTime))
                )
        )._toQuery();

        Query endTimeRange = RangeQuery.of(q -> q
                .term(n -> n
                        .field("endTimestamp")
                        .gt(String.valueOf(startTime))
                        .lt(String.valueOf(endTime))
                )
        )._toQuery();

        try {
            SearchResponse<AttackLog> response = esClient.search(b -> b
                    .index("attack_log")
                    .query(q -> q
                            .bool(b1 -> b1
                                    .should(startTimeRange)
                                    .should(endTimeRange)
                                    .minimumShouldMatch("1")
                            )
                    )
                    .size(DEFAULT_MAX_SIZE)
                    .timeout("120s"), AttackLog.class
            );

            return response.hits().hits().stream().map(Hit::source).toList().getFirst().getEventId();
        } catch (IOException e) {
            log.error("Search attack log by time range error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * @Description Query page data and encapsulate it into PageVO
     * @Date 2025/12/14
     **/
    public <T extends IndexBase> PageVO<T> searchPage2PageVO(int limit, int start, Class<T> clazz) {
        try {
            SearchResponse<T> response = esClient.search(b -> b
                    .index(indexName(clazz))
                    .from(start)
                    .size(limit)
                    .timeout("120s")
                    .trackTotalHits(t -> t.enabled(true))
                    .sort(s -> s
                            .field(f -> f
                                    .field("timestamp")
                                    .order(SortOrder.Desc)
                            )
                    ), clazz
            );

            HitsMetadata<T> hits = response.hits();

            long total = hits.total() != null ? hits.total().value() : 0L;
            List<T> dataList = hits.hits().stream().map(Hit::source).toList();

            return new PageVO<>(total, dataList);
        } catch (IOException e) {
            log.error("Search page logs error: {}", e.getMessage());
            return new PageVO<>(0, List.of());
        }
    }
}