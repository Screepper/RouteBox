package com.routebox.manager.attack.node;

import com.routebox.common.constants.ConfConstants;
import com.routebox.entity.index.AttackLog;
import com.routebox.manager.InteractionManager;
import com.routebox.util.TimeUtil;
import com.routebox.manager.InteractionManager.WsOptions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @Description Node Memory Leak Attack Handler
 * @Date 2025/6/26
**/
public class MemoryLeakAttackHandler extends NodeAttackHandler {
    private static final String baseScriptContent = """
            #!/bin/bash
            
            trap 'kill -- -$$ 2>/dev/null; exit 0' SIGTERM SIGINT EXIT
            
            total_mem=$(grep MemTotal /proc/meminfo | awk '{print $2 * 1024}')
            threshold=$((total_mem * 40 / 100))
            chunk_size=$((400*1024*1024))
            max_chunks=$((threshold / chunk_size / 20))
            
            for ((i=0; i<20; i++)); do
                (
                    declare -a mem_chunks
                    count=0
                    while :; do
                        if (( count >= max_chunks )); then
                            sleep 1600
                        fi
            
                        mem_chunks+=($(dd if=/dev/zero bs=$chunk_size count=1 2>/dev/null | base64 -w0))
                        ((count++))
                        sleep 0.1
                    done
                ) &
            done
            
            sleep 1800
            """;

    public MemoryLeakAttackHandler(List<Integer> targetNodeIds) {
        super(targetNodeIds, "Memory Leakage");
    }

    @Override
    public boolean attack() {
        // Construct Attack Commands
        List<String> cmdList = new ArrayList<>();
        cmdList.add("mkdir -p " + ConfConstants.containerFilePath());
        String scriptPath = ConfConstants.containerFilePath() + "/memory_leak.sh";
        cmdList.add("touch " + scriptPath);
        String encodedScript = Base64.getEncoder().encodeToString(baseScriptContent.getBytes(StandardCharsets.UTF_8));
        cmdList.add("echo " + encodedScript + " | base64 -d > " + scriptPath);
        cmdList.add("(bash " + scriptPath + " &)");
        InteractionManager.sendCmdList(getTargetIds(), cmdList);

        postAttack(getAttackLog("Launched " +
                ATTACK_TYPE +
                " attack at " +
                TimeUtil.getNowTimeStr() +
                ", nodes " + getTargetIds() + " are affected."
        ));

        return true;
    }

    private AttackLog getAttackLog(String attackProcess) {
        AttackLog attackLog = new AttackLog();
        attackLog.setVictims(getTargetIds());
        attackLog.setAttackProcess(attackProcess);

        return attackLog;
    }

    @Override
    public void restoreState() {
        InteractionManager.sendCmd(getTargetIds(), "pkill -9 -f \"memory_leak.sh\"");
    }
}