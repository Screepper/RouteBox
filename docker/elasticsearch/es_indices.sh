#!/bin/bash

# Elasticsearch connection configuration
ES_HOST="http://localhost:12102"
ES_USER="elastic"
ES_PASSWORD="routebox"
CURL_TIMEOUT=30

# Create ES index with given index name and mapping (Index name, Index mapping JSON string)
create_es_index() {
    local index_name="$1"
    local mapping="$2"
    
    echo "============================================="
    echo "Processing index: $index_name"
    
    local curl_command="curl -s -w '\n%{http_code}' --connect-timeout $CURL_TIMEOUT -u $ES_USER:$ES_PASSWORD -X PUT \"$ES_HOST/$index_name\" -H \"Content-Type: application/json\" -d '$mapping'"
    
    # Execute curl command and capture response & HTTP status code
    local response
    local http_code
    response=$(eval "$curl_command")
    http_code=$(echo "$response" | tail -n1)
    response_body=$(echo "$response" | head -n -1)
    
    # Check creation result based on HTTP status code and response content
    if [ "$http_code" -eq 200 ] && echo "$response_body" | grep -q '"acknowledged":true'; then
        echo "Success: Index $index_name created successfully"
        return 0
    elif [ "$http_code" -eq 400 ] && echo "$response_body" | grep -q 'resource_already_exists_exception'; then
        echo "Warning: Index $index_name already exists, skipped"
        return 0
    else
        echo "Error: Failed to create index $index_name"
        echo "   HTTP Status: $http_code"
        echo "   Response: $response_body"
        return 1
    fi
}

# Define mappings for all required indexes
ATTACK_LOG_MAPPING='{
  "mappings": {
    "properties": {
      "logId": {
        "type": "keyword"
      },
      "eventId": {
        "type": "keyword"
      },
      "networkId": {
        "type": "integer"
      },
      "nodeId": {
        "type": "integer"
      },
      "timestamp": {
        "type": "date",
        "format": "epoch_second"
      },
      "endTimestamp": {
        "type": "date",
        "format": "epoch_second"
      },
      "attackType": {
        "type": "keyword"
      },
      "attackers": {
        "type": "integer"
      },
      "victims": {
        "type": "integer"
      },
      "attackProcess": {
        "type": "text"
      }
    }
  }
}'

# Mapping for bgp_log index (Record BGP related information at AS node)
BGP_LOG_MAPPING='{
  "mappings": {
    "properties": {
      "logId": {
        "type": "keyword"
      },
      "eventId": {
        "type": "keyword"
      },
      "networkId": {
        "type": "integer"
      },
      "nodeId": {
        "type": "integer"
      },
      "timestamp": {
        "type": "date",
        "format": "epoch_second"
      },
      "routerId": {
        "type": "text"
      },
      "asNum": {
        "type": "integer"
      },
      "ribEntriesNum": {
        "type": "integer"
      },
      "peersNum": {
        "type": "integer"
      },
      "totalNeighborsNum": {
        "type": "integer"
      },
      "neighbors": {
        "type": "nested",
        "properties": {
          "neighborRouteId": {
            "type": "text"
          },
          "neighborAsNum": {
            "type": "integer"
          },
          "messageReceivedNum": {
            "type": "integer"
          },
          "messageSentNum": {
            "type": "integer"
          },
          "upDownTime": {
            "type": "text"
          },
          "stateOrReceivedNum": {
            "type": "text"
          },
          "prefixSentNum": {
            "type": "integer"
          }
        }
      },
      "routeEntries": {
        "type": "nested",
        "properties": {
          "ipPrefix": {
            "type": "text"
          },
          "nextHop": {
            "type": "text"
          },
          "weight": {
            "type": "integer"
          },
          "path": {
            "type": "text"
          },
          "isBest": {
            "type": "boolean"
          }
        }
      },
      "networkIps": {
        "type": "nested",
        "properties": {
          "ipPrefix": {
            "type": "text"
          },
          "routeMapName": {
            "type": "text"
          },
          "mask": {
            "type": "text"
          }
        }
      },
      "routeMaps":{
        "type": "nested",
        "properties": {
          "name": {
            "type": "text"
          },
          "action": {
            "type": "text"
          },
          "sequenceNumber": {
            "type": "integer"
          },
          "matchStatements":{
            "type": "text"
          },
          "setStatements":{
            "type": "text"
          }
        }
      },
      "neighborRouteMaps": {
        "type": "nested",
        "properties": {
          "neighborIp": {
            "type": "text"
          },
          "routeMapName": {
            "type": "text"
          },
          "direction": {
            "type": "text"
          }
        }
      },
      "runningConfig": {
        "type": "text"
      }
    }
  }
}'

# Mapping for bgp_update_log index (Record BGP Update messages parsed from MRT files)
BGP_UPDATE_LOG_MAPPING='{
  "mappings": {
    "properties": {
      "logId": {
        "type": "keyword"
      },
      "eventId": {
        "type": "keyword"
      },
      "networkId": {
        "type": "integer"
      },
      "nodeId": {
        "type": "integer"
      },
      "timestamp": {
        "type": "date",
        "format": "epoch_second"
      },
      "updateType": {
        "type": "text"
      },
      "peerIp": {
        "type": "text"
      },
      "peerAs": {
        "type": "text"
      },
      "ipPrefix": {
        "type": "text"
      },
      "asPath": {
        "type": "text"
      },
      "origin": {
        "type": "text"
      },
      "nextHop": {
        "type": "text"
      },
      "localPref": {
        "type": "integer"
      },
      "med": {
        "type": "integer"
      },
      "community": {
        "type": "text"
      },
      "atomicAggregator": {
        "type": "text"
      },
      "aggregator": {
        "type": "text"
      },
      "originalId": {
        "type": "integer"
      },
      "clusterList": {
        "type": "text"
      },
      "dpa": {
        "type": "text"
      },
      "advertiser": {
        "type": "text"
      },
      "clusterId": {
        "type": "text"
      },
      "mpReach": {
        "type": "nested",
        "properties": {
          "nextHop": {
            "type": "text"
          },
          "nlri": {
            "type": "text"
          }
        }
      },
      "mpUnReach": {
        "type": "text"
      },
      "extCommunities": {
        "type": "text"
      },
      "asPathLimit": {
        "type": "text"
      }
    }
  }
}'

# Mapping for router_log index (Record all router configuration commands at AS node)
ROUTER_LOG_MAPPING='{
  "mappings": {
    "properties": {
      "logId": {
        "type": "keyword"
      },
      "eventId": {
        "type": "keyword"
      },
      "networkId": {
        "type": "integer"
      },
      "nodeId": {
        "type": "integer"
      },
      "timestamp": {
        "type": "date",
        "format": "epoch_second"
      },
      "histories": {
        "type": "nested",
        "properties": {
          "time": {
            "type": "date",
            "format": "epoch_second"
          },
          "command": {
            "type": "text"
          }
        }
      }
    }
  }
}'

# Mapping for ping_log index (Record ping results between AS nodes)
PING_LOG_MAPPING='{
  "mappings": {
    "properties": {
      "logId": {
        "type": "keyword"
      },
      "eventId": {
        "type": "keyword"
      },
      "networkId": {
        "type": "integer"
      },
      "nodeId": {
        "type": "integer"
      },
      "timestamp": {
        "type": "date",
        "format": "epoch_second"
      },
      "pingNodeId": {
        "type": "integer"
      },
      "pingIp": {
        "type": "ip"
      },
      "delayMs": {
        "type": "float"
      }
    }
  }
}'

# Mapping for system_network_log index (Record system network performance metrics)
SYSTEM_NETWORK_LOG_MAPPING='{
  "mappings": {
    "properties": {
      "logId": {
        "type": "keyword"
      },
      "eventId": {
        "type": "keyword"
      },
      "networkId": {
        "type": "integer"
      },
      "nodeId": {
        "type": "integer"
      },
      "timestamp": {
        "type": "date",
        "format": "epoch_second"
      },
      "cpuUseRatio": {
        "type": "float"
      },
      "systemUseRatio": {
        "type": "float"
      },
      "usedMemoryKb": {
        "type": "long"
      },
      "memoryUseRatio": {
        "type": "float"
      },
      "tps": {
        "type": "integer"
      },
      "ioWriteKb": {
        "type": "float"
      },
      "nics": {
        "type": "nested",
        "properties": {
          "name": {
            "type": "text"
          },
          "receivePacketsRate": {
            "type": "integer"
          },
          "transmitPacketsRate": {
            "type": "integer"
          },
          "receiveKbRate": {
            "type": "float"
          },
          "transmitKbRate": {
            "type": "float"
          }
        }
      },
      "totalReceivePacketsRate": {
        "type": "integer"
      },
      "totalTransmitPacketsRate": {
        "type": "integer"
      },
      "totalReceiveKbRate": {
        "type": "float"
      },
      "totalTransmitKbRate": {
        "type": "float"
      }
    }
  }
}'

# Mapping for topology_log index (Record network topology information)
TOPOLOGY_LOG_MAPPING='{
  "mappings": {
    "properties": {
      "logId": {
        "type": "keyword"
      },
      "eventId": {
        "type": "keyword"
      },
      "networkId": {
        "type": "integer"
      },
      "nodeId": {
        "type": "integer"
      },
      "timestamp": {
        "type": "date",
        "format": "epoch_second"
      },
      "adjacency": {
        "type": "nested",
        "properties": {
          "nodeId": {
            "type": "integer"
          },
          "adjacentNodeIds": {
            "type": "integer"
          }
        }
      },
      "nodeHealthyRatio": {
        "type": "float"
      },
      "linkHealthyRatio": {
        "type": "float"
      },
      "nodes": {
        "type": "nested",
        "properties": {
          "nodeId": {
            "type": "integer"
          },
          "type": {
            "type": "text"
          },
          "status": {
            "type": "text"
          },
          "physical": {
            "type": "boolean"
          }
        }
      },
      "links": {
        "type": "nested",
        "properties": {
          "linkId": {
            "type": "text"
          },
          "sourceNodeId": {
            "type": "integer"
          },
          "targetNodeId": {
            "type": "integer"
          },
          "status": {
            "type": "text"
          }
        }
      }
    }
  }
}'

# Mapping for detect_result index (Record detection results)
DETECT_RESULT_LOG_MAPPING='{
  "mappings": {
    "properties": {
      "logId": {
        "type": "keyword"
      },
      "eventId": {
        "type": "keyword"
      },
      "networkId": {
        "type": "integer"
      },
      "nodeId": {
        "type": "integer"
      },
      "timestamp": {
        "type": "date",
        "format": "epoch_second"
      },
      "dataSource":{
        "type": "keyword"
      },
      "isAnomaly":{
        "type": "boolean"
      },
      "topologyInfo": {
        "type": "text"
      },
      "systemNetworkAnomaly": {
        "type": "text"
      },
      "bgpAnomaly": {
        "type": "text"
      },
      "routerAnomaly": {
        "type": "text"
      },
      "pingAnomaly": {
        "type": "text"
      },
      "topologyAnomaly": {
        "type": "text"
      },
      "directEvidence":{
        "type": "text"
      },
      "indirectEvidence":{
        "type": "text"
      },
      "possibilities": {
        "type": "nested",
        "properties": {
          "method": {
            "type": "text"
          },
          "possibility": {
            "type": "text"
          },
          "rationale": {
            "type": "text"
          }
        }
      },
      "attackType": {
        "type": "keyword"
      },
      "attackers":{
        "type": "integer"
      },
      "victims":{
        "type": "integer"
      },
      "anomalyStart": {
        "type": "text"
      },
      "anomalyEnd": {
        "type": "text"
      }
    }
  }
}'

# Main Execution: Create all indexes one by one
echo "Starting to create RouteBox system Elasticsearch indexes..."
echo "Target ES Server: $ES_HOST"
echo "========================================================"

create_es_index "attack_log" "$ATTACK_LOG_MAPPING"
create_es_index "bgp_log" "$BGP_LOG_MAPPING"
create_es_index "bgp_update_log" "$BGP_UPDATE_LOG_MAPPING"
create_es_index "router_log" "$ROUTER_LOG_MAPPING"
create_es_index "ping_log" "$PING_LOG_MAPPING"
create_es_index "system_network_log" "$SYSTEM_NETWORK_LOG_MAPPING"
create_es_index "topology_log" "$TOPOLOGY_LOG_MAPPING"
create_es_index "detect_result_log" "$DETECT_RESULT_LOG_MAPPING"

echo "========================================================"
echo "All index creation operations completed!"