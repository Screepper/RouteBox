package com.routebox.manager;

import com.routebox.common.constants.ConfConstants;
import com.routebox.entity.attack.LinkUpdateInfo;
import com.routebox.entity.topology.BatchChangeStatus;
import com.routebox.entity.topology.DTO.TopologyInfoDTO;
import com.routebox.entity.topology.LogDownload;
import com.routebox.entity.topology.NodesNet;
import com.routebox.entity.sernes.WsErrorResponse;
import com.routebox.entity.sernes.WsResponse;
import com.routebox.util.HttpUtil;
import com.routebox.util.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Description SERNES Interaction Manager
 * @Date 2025/12/18
 **/
@Slf4j
public class InteractionManager {
    // Http
    public static final String GET_TOPOLOGY_INFO = "/bgpnet/topology/";
    public static final String CHANGE_NODES_NET = "/vnode/network/extac/batch/";
    public static final String CHANGE_NODES_STATUS = "/vnode/update/batch/";
    public static final String CHANGE_LINK_STATUS = "/vlink/update/batch/";
    public static final String DOWNLOAD_LOG_FILES = "/tools/log/download/";
    public static final String DOWNLOAD_NETWORK_CONFIG_FILES = "/network/%s/file/current/download/";
    // Ws
    public static final String DOCKER_EXEC_WS = "/ws/vnode/%s/exec/";

    private static final WebSocketClient client = new StandardWebSocketClient();
    private static final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    private static final ConcurrentHashMap<Integer, List<String>> wsResponseMap = new ConcurrentHashMap<>(); // Line-Split WS Messages
    private static final ConcurrentHashMap<Integer, CompletableFuture<Boolean>> futures = new ConcurrentHashMap<>(); // Async Message processor
    private static volatile Semaphore CONNECTION_SEMAPHORE; // Default Max Concurrent Web Socket Connections

    public enum WsOptions {
        SYNC_RUN, // Default: Synchronous Run
        @Deprecated
        ASYNC_RUN, // !! Not recommended to use this, it may work in low-delay scenario, but not in high-delay scenario !!
        NO_OUTPUT, // Default: Do Not Output
        REAL_TIME_OUTPUT
    }

    //============================== Http ==============================

    /**
     * @Description Get topology info
     * @Date 2025/12/18
     **/
    public static TopologyInfoDTO getTopologyInfo() {
        return HttpUtil.get(ConfConstants.httpBaseUrl() + GET_TOPOLOGY_INFO, TopologyInfoDTO.class);
    }

    /**
     * @Description Batch change nodes network
     * @Date 2025/12/18
     **/
    public static void changeNodesNet(NodesNet nodesNet) {
        HttpUtil.post(ConfConstants.httpBaseUrl() + CHANGE_NODES_NET, nodesNet);
    }

    /**
     * @Description Batch change nodes status
     * @Date 2025/12/18
     **/
    public static void changeNodesStatus(BatchChangeStatus nodesStatus) {
        HttpUtil.post(ConfConstants.httpBaseUrl() + CHANGE_NODES_STATUS, nodesStatus);
    }

    /**
     * @Description Change link status
     * @Date 2025/12/18
     **/
    public static void changeLinkStatus(LinkUpdateInfo linkUpdate) {
        HttpUtil.post(ConfConstants.httpBaseUrl() + CHANGE_LINK_STATUS, linkUpdate);
    }

    /**
     * @Description Download topology files
     * @Date 2025/12/18
     **/
    public static byte[] downloadLogFiles(List<Integer> nodeIds, String volume) {
        return HttpUtil.post(
                ConfConstants.httpBaseUrl() + DOWNLOAD_LOG_FILES,
                new LogDownload(nodeIds, volume),
                byte[].class,
                HttpUtil.HttpOptions.VERY_LONG_TIMEOUT
        );
    }

    /**
     * @Description Download network config files
     * @Date 2025/12/18
     **/
    public static byte[] downloadNetworkConfigFiles() {
        return HttpUtil.get(
                String.format(ConfConstants.httpBaseUrl() + DOWNLOAD_NETWORK_CONFIG_FILES, TopologyManager.topologyInfo.getNetworkId()),
                byte[].class,
                HttpUtil.HttpOptions.VERY_LONG_TIMEOUT
        );
    }


    //============================== Ws ==============================

    /**
     * @Description Lazy load ws semaphore
     * @Date 2025/12/18
     **/
    private static Semaphore getSemaphore() {
        if (CONNECTION_SEMAPHORE == null) {
            synchronized (InteractionManager.class) {
                if (CONNECTION_SEMAPHORE == null) {
                    CONNECTION_SEMAPHORE = new Semaphore(ConfConstants.wsMaxConnections());
                }
            }
        }

        return CONNECTION_SEMAPHORE;
    }

    /**
     * @Description Clear ws response map and futures
     * @Date 2025/3/30
     **/
    public static void wsClear() {
        wsResponseMap.clear();
        futures.clear();
    }

    /**
     * @Description Get ws session, create if not exists
     * @Date 2025/3/30
     **/
    public static WebSocketSession getWsSession(Integer nodeId, boolean isRealTimeOutput) throws Exception {
        return client.execute(
                new CustomWebSocketHandler(nodeId, isRealTimeOutput),
                new WebSocketHttpHeaders(),
                new URI(String.format(ConfConstants.wsBaseUrl() + DOCKER_EXEC_WS, nodeId))
        ).get();
    }

    /**
     * @Description Send ws commands synchronously
     * @Date 2025/3/30
     **/
    public static void sendCmdSync(Integer nodeId, List<String> cmdList, boolean isRealTimeOutput) throws Exception {
        getSemaphore().acquire();
        log.info("Node{}|Fetch Semaphore|Sync|Remain {}", nodeId, getSemaphore().availablePermits());
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            futures.put(nodeId, future);
            WebSocketSession session = getWsSession(nodeId, isRealTimeOutput);
            session.sendMessage(new TextMessage(JsonUtil.toJson(new CmdList(cmdList))));
            log.info("Node{}|Send Sync|Row {}", nodeId, cmdList.size());

            future.whenComplete((r, e) -> getSemaphore().release()).get(ConfConstants.wsTimeoutS(), TimeUnit.SECONDS);
        } catch (Exception e) {
            // Release Semaphore on Exception
            getSemaphore().release();
            log.error("Node{}|Send Sync Error|Remain {}", nodeId, getSemaphore().availablePermits());
        }
    }

    /**
     * @Description Send ws commands asynchronously
     * @Date 2025/4/14
     **/
    public static void sendCmdAsync(Integer nodeId, List<String> cmdList, boolean isRealTimeOutput) throws Exception {
        getSemaphore().acquire();
        log.info("Node{}|Fetch Semaphore|Async|Remain {}", nodeId, getSemaphore().availablePermits());

        try {
            WebSocketSession session = getWsSession(nodeId, isRealTimeOutput);
            session.sendMessage(new TextMessage(JsonUtil.toJson(new CmdList(cmdList))));
            log.info("Node{}|Send Async|Rows {}", nodeId, cmdList.size());

            // Immediately Close Session after sending
            session.close();
            getSemaphore().release();
        } catch (Exception e) {
            // Release Semaphore on Exception
            getSemaphore().release();
            log.error("Node{}|Send Async Error|Remain {}", nodeId, getSemaphore().availablePermits());
        }
    }

    /**
     * @Description Get all ws responses
     * @Date 2025/3/30
     **/
    public static ConcurrentHashMap<Integer, List<String>> getWsResponse() {
        return wsResponseMap;
    }

    /**
     * @Description Get ws responses for 1 node
     * @Date 2025/4/18
     **/
    public static List<String> getWsResponse(Integer nodeId) {
        return wsResponseMap.getOrDefault(nodeId, Collections.emptyList());
    }

    /**
     * @Description Get ws responses for specified nodes
     * @Date 2025/4/18
     **/
    public static ConcurrentHashMap<Integer, List<String>> getWsResponse(List<Integer> nodeIds) {
        ConcurrentHashMap<Integer, List<String>> result = new ConcurrentHashMap<>();

        for (Integer nodeId : nodeIds) {
            result.put(nodeId, wsResponseMap.get(nodeId));
        }

        return result;
    }

    /**
     * @Description Append ws response
     * @Date 2025/3/30
     **/
    public static void appendWsResponse(Integer nodeId, String response) {
        List<String> savedLines = wsResponseMap.getOrDefault(nodeId, new ArrayList<>());

        savedLines.add(response);

        wsResponseMap.put(nodeId, savedLines);
    }

    /**
     * @Description Clear ws response map
     * @Date 2025/3/30
     **/
    public static void clearWsResponse() {
        wsResponseMap.clear();
    }

    /**
     * @Description Custom ws handler
     * @Date 2025/3/29
     **/
    @AllArgsConstructor
    private static class CustomWebSocketHandler extends TextWebSocketHandler {

        private final Integer nodeId;
        private final boolean isRealTimeOutput;

        /**
         * @Description Handle text message
         * @Date 2025/3/29
         **/
        @Override
        protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) {
            String payload = message.getPayload();
            // If error response, it will still fill the type as "error", so do not need to throw exception
            WsResponse wsResponse = JsonUtil.fromJson(payload, WsResponse.class);

            if ("error".equals(wsResponse.getType())) {
                WsErrorResponse wsErrorResponse = JsonUtil.fromJson(payload, WsErrorResponse.class);
                log.error("Command Execute Error for Node{}: {}", nodeId, wsErrorResponse.getMsg());

                CompletableFuture<Boolean> future = futures.remove(nodeId);

                if (future != null) {
                    future.completeExceptionally(new RuntimeException(wsErrorResponse.getMsg()));
                }

                return;
            }

            // Real-time output
            if (isRealTimeOutput) {
                log.info("Node{}: {}", nodeId, wsResponse.getData());
            }

            appendWsResponse(nodeId, wsResponse.getData());
        }

        /**
         * @Description Remove future when connection closed
         * @Date 2025/4/4
         **/
        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
            CompletableFuture<Boolean> future = futures.remove(nodeId);

            if (future != null) {
                future.complete(true);
            }
        }
    }

    /**
     * @Description Send commands
     * @Date 2025/12/18
     **/
    private static void sendCommands(List<Integer> nodeIds, HashMap<Integer, List<String>> cmdListMap, List<String> sameCmdList, WsOptions... ops) {
        List<CompletableFuture<Void>> allFutures = new ArrayList<>();

        boolean isAsync = false;
        boolean isRealTimeOutput = false;

        for (WsOptions op : ops) {
            if (op == WsOptions.SYNC_RUN) {
                isAsync = false;
            } else if (op == WsOptions.ASYNC_RUN) {
                isAsync = true;
            }
            if (op == WsOptions.REAL_TIME_OUTPUT) {
                isRealTimeOutput = true;
            } else if (op == WsOptions.NO_OUTPUT) {
                isRealTimeOutput = false;
            }
        }

        final boolean finalIsAsync = isAsync;
        final boolean finalIsRealTimeOutput = isRealTimeOutput;

        for (Integer nodeId : nodeIds) {
            CompletableFuture<Void> nodeFuture = CompletableFuture.runAsync(() -> {
                AtomicInteger retryCount = new AtomicInteger(0);
                boolean success = false;

                while (!success && retryCount.get() <= ConfConstants.wsMaxRetries()) {
                    try {
                        if (sameCmdList != null && !sameCmdList.isEmpty()) {
                            if (finalIsAsync) {
                                sendCmdAsync(nodeId, sameCmdList, finalIsRealTimeOutput);
                            } else {
                                sendCmdSync(nodeId, sameCmdList, finalIsRealTimeOutput);
                            }
                        } else if (cmdListMap != null && cmdListMap.containsKey(nodeId) && !cmdListMap.get(nodeId).isEmpty()) {
                            if (finalIsAsync) {
                                sendCmdAsync(nodeId, cmdListMap.get(nodeId), finalIsRealTimeOutput);
                            } else {
                                sendCmdSync(nodeId, cmdListMap.get(nodeId), finalIsRealTimeOutput);
                            }
                        }

                        success = true;
                    } catch (Exception e) {
                        if (retryCount.incrementAndGet() > ConfConstants.wsMaxRetries()) {
                            log.error("Node {} send commands error，exceeding the max retry times: {}, reason: {}", nodeId, ConfConstants.wsMaxRetries(), e.getMessage());
                        } else {
                            try {
                                TimeUnit.MILLISECONDS.sleep(ConfConstants.wsRetryWaitMs());

                                log.info("Node {} is resending commands", nodeId);
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                }
            }, executorService);

            allFutures.add(nodeFuture);
        }

        // Waiting all tasks to complete
        try {
            CompletableFuture.allOf(allFutures.toArray(CompletableFuture[]::new)).get(ConfConstants.wsTimeoutS() * nodeIds.size(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Sending commands error: {}", e.getMessage());
        }
    }


    /**
     * @Description Execute commands
     * @Date 2025/3/29
     **/
    public static void sendCmdList(HashMap<Integer, List<String>> cmdListMap, WsOptions... ops) {
        sendCommands(new ArrayList<>(cmdListMap.keySet()), cmdListMap, null, ops);
    }

    /**
     * @Description Execute commands
     * @Date 2025/3/29
     **/
    public static void sendCmdList(List<Integer> nodeIds, List<String> cmdList, WsOptions... ops) {
        sendCommands(nodeIds, null, cmdList, ops);
    }

    /**
     * @Description Execute commands
     * @Date 2025/3/29
     **/
    public static void sendCmd(List<Integer> nodeIds, String cmd, WsOptions... ops) {
        if (cmd == null || cmd.isEmpty()) return;

        sendCommands(nodeIds, null, Collections.singletonList(cmd), ops);
    }

    /**
     * @Description Execute commands
     * @Date 2025/3/29
     **/
    public static void sendCmdList(Integer nodeId, List<String> cmdList, WsOptions... ops) {
        sendCommands(Collections.singletonList(nodeId), null, cmdList, ops);
    }

    /**
     * @Description Execute commands
     * @Date 2025/3/29
     **/
    public static void sendCmd(Integer nodeId, String cmd, WsOptions... ops) {
        if (cmd == null || cmd.isEmpty()) return;

        sendCommands(Collections.singletonList(nodeId), null, Collections.singletonList(cmd), ops);
    }

    @Data
    @AllArgsConstructor
    private static class CmdList {
        private List<String> cmd_list;
    }
}
