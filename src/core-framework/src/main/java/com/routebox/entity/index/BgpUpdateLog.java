package com.routebox.entity.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BgpUpdateLog extends IndexBase {
    // ======= Fixed fields =======
    private String updateType; // Advertisement/Withdrawal
    private String peerIp;
    private String peerAS;
    private String ipPrefix; // announced/withdrawn prefix
    // ======= Only for Advertisement (22 attributes, 17 used, 0-15 and 21) =======
    private String asPath; // 0
    private String origin; // 1
    private String nextHop; // 2
    private Long localPref; // 3
    private Long med; // 4
    private String[] community; // 5
    private String atomicAggregator; // 6
    private String aggregator; // 7
    private Long originatorId; // 8
    private String[] clusterList; // 9
    private String dpa; // 10
    private String advertiser; // 11
    private String clusterId; // 12
    private MpReach mpReach; // 13
    private String mpUnReach; // 14
    private String extCommunities; // 15
    private String asPathLimit; // 21

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MpReach {
        private String[] nextHop;
        private String[] nlri;
    }
}