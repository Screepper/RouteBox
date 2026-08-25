package com.routebox.manager.attack.node;

import com.routebox.common.constants.ConfConstants;
import com.routebox.entity.index.AttackLog;
import com.routebox.manager.InteractionManager;
import com.routebox.util.TimeUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @Description Node Overload Attack Handler
 * @Date 2025/6/25
**/
public class SystemOverloadAttackHandler extends NodeAttackHandler {

    private static final String baseScriptContent = """
            #!/bin/bash
            
            trap 'kill -- -$$ 2>/dev/null; exit 0' SIGTERM SIGINT EXIT
            cores=$(nproc)
            
            for ((i=0; i<cores*30; i++)); do
                (
                    while :; do
                        for ((a=1; a<200; a++)); do
                            for ((b=1; b<200; b++)); do
                                for ((c=1; c<200; c++)); do
                                    for ((d=1; d<200; d++)); do
                                        val=$(( (a * b) ^ (c * d) | (a << d) + (c >> b) ))
                                        val=$(( val * 31415926535 / 16180339887 ))
                                        val=$(( val & 0xFFFFFFFF ))
                                    done
                                done
                            done
                        done
                    done
                ) &
            done
            
            sleep 1800
            """;

    public SystemOverloadAttackHandler(List<Integer> targetNodeIds) {
        super(targetNodeIds, "System Overload");
    }

    @Override
    public boolean attack() {
        // Construct Attack Commands
        List<String> cmdList = new ArrayList<>();
        cmdList.add("mkdir -p " + ConfConstants.containerFilePath());
        String scriptPath = ConfConstants.containerFilePath() + "/cpu_overload.sh";
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
        InteractionManager.sendCmd(getTargetIds(), "pkill -9 -f \"cpu_overload.sh\"");
    }
}
