package com.routebox.common.constants;

/**
 * @Description Fixed constants
 * @Date 2025/12/17
**/
public interface Constants {
    // ==================== Topology-related ====================
    String NODE_TYPE_HOST = "host";
    String NODE_TYPE_AS = "as";
    String STATUS_UP = "up";
    String STATUS_DOWN = "down";
    String LINK_OP_TRAFFIC = "traffic_control";
    int DEFAULT_LINK_BW = 1000;
    int DEFAULT_LINK_DELAY = 0;
    float DEFAULT_LINK_LOSS = .0f;

    // ==================== Attack-related ====================
    // Attack Types
    int ATTACK_PREFIX_HIJACKING = 0;
    int ATTACK_SUB_PREFIX_HIJACKING = 1;
    // int ATTACK_FAKE_PREFIX_HIJACKING = 2;
//    int ATTACK_PREFIX_INTERCEPT = 3;
    int ATTACK_ROUTE_FLAPPING = 3;
    int ATTACK_ROUTE_LEAKAGE = 4;
    int ATTACK_NODE_DOWN = 0;
    int ATTACK_SYSTEM_OVERLOAD = 1;
    int ATTACK_MEMORY_LEAKAGE = 2;
    int ATTACK_LINK_DOWN = 0;
    int ATTACK_LINK_CONGESTION = 1;
    int ATTACK_LINK_JITTER = 2;
//    int ATTACK_LINK_LOSS = 3;

    // ==================== Log-related ====================
    // Files
    String LOG_NAME_SYSTEM_NETWORK = "system_network.log";
    String LOG_NAME_PING = "ping.log";
    String LOG_NAME_ROUTER = "router.log";
    String LOG_NAME_BGP_CONFIG = "bgp.log";
    String LOG_NAME_BGP_UPDATE_BIN = "bgp_updates.bin";
    String LOG_NAME_VERSION = "version.lock";
}