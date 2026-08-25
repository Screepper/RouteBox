//package com.routebox.manager.attack.bgp;
//
//import com.routebox.common.constants.ConfConstants;
//import com.routebox.common.constants.Constants;
//import com.routebox.entity.index.AttackLog;
//import com.routebox.manager.AttackManager;
//import com.routebox.manager.InteractionManager;
//import com.routebox.util.IpBgpUtil;
//import com.routebox.util.TimeUtil;
//import com.routebox.manager.InteractionManager.WsOptions;
//
//import java.nio.charset.StandardCharsets;
//import java.util.ArrayList;
//import java.util.Base64;
//import java.util.HashMap;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//
///**
// * @Description Prefix Intercept Attack Handler
// * @Date 2025/3/29
// * @Author Screep
// **/
//public class PrefixInterceptAttackHandler extends BgpAttackHandler {
//
//    private static final String customerPy = """
//            import time, requests
//
//            end_time = time.time() + 40
//            while time.time() < end_time:
//                try:
//                    response = requests.get('http://%s')
//                    print(response.text, flush=True)
//                except Exception as e:
//                    print("Cannot connect now")
//                time.sleep(3)
//            """;
//    private static final String servicePy = """
//            from flask import Flask
//            from multiprocessing import Process
//            import time
//
//            app = Flask(__name__)
//
//            @app.route('/')
//            def home():
//                return '%s'
//
//            def run_server():
//                app.run(host='0.0.0.0', port=80)
//
//            if __name__ == '__main__':
//                server = Process(target=run_server)
//                server.start()
//                time.sleep(40)
//                server.terminate()
//                server.join()
//            """;
//
//    public PrefixInterceptAttackHandler(Integer sourceNodeId, List<Integer> targetIds) {
//        super(sourceNodeId, targetIds, "Prefix Interception");
//    }
//
//    @Override
//    public boolean attack() {
//        // Get customerNodeId, victimNodeId, and attackerId
//        int customerNodeId = getTargetIds().get(0);
//        int victimNodeId = getTargetIds().get(1);
//        int attackerId = getAttackerId();
//        int[] ids = {customerNodeId, victimNodeId, attackerId};
//
//        InteractionManager.clearWsResponse();
//        InteractionManager.sendCmd(victimNodeId, "vtysh -c 'show running-config'");
//        List<String> wsResponse = InteractionManager.getWsResponse(victimNodeId);
//
//        List<String> networkPrefixes = IpBgpUtil.parseNetworkIps(wsResponse);
//        // Randomly select one IP prefix for attack
//        if (!networkPrefixes.isEmpty()) {
//            focusedIdIpMap.put(victimNodeId, networkPrefixes.get(random.nextInt(networkPrefixes.size())));
//        }
//        if (focusedIdIpMap.isEmpty()) return false;
//
//        String attackProcess =  "Node ["
//                + getAttackerId()
//                + "] launched "
//                + ATTACK_TYPE
//                + " attack at "
//                + TimeUtil.getNowTimeStr()
//                + ", Node ["
//                + victimNodeId
//                + "]'s traffic is intercepted.";
//
//        String hostIp = IpBgpUtil.generateHostIp(focusedIdIpMap.get(victimNodeId));
//        String customerPyContent = String.format(customerPy, hostIp);
//        String victimPyContent = String.format(servicePy, "Legal Response: 111");
//        String attackerPyContent = String.format(servicePy, "Illegal Response: 000");
//
//        HashMap<Integer, List<String>> cmdListMap = new HashMap<>();
//        for (int id : ids) {
//            List<String> cmdList = new ArrayList<>();
//            if (id == attackerId) {
//                cmdList.add("vtysh -c 'configure' -c 'router bgp' -c 'network " + focusedIdIpMap.get(victimNodeId) + "'");
//                cmdList.add("ip addr add " + hostIp + " dev eth0");
//            }
//            cmdList.add("mkdir -p " + ConfConstants.containerFilePath());
//            String pyPath = ConfConstants.containerFilePath() + (id == customerNodeId ? "/customer.py" : "/service.py");
//            cmdList.add("touch " + pyPath);
//
//            String pyContent;
//            if (id == customerNodeId) {
//                pyContent = customerPyContent;
//            } else {
//                pyContent = id == attackerId ? attackerPyContent : victimPyContent;
//            }
//            String encodedScript = Base64.getEncoder().encodeToString(pyContent.getBytes(StandardCharsets.UTF_8));
//            cmdList.add("echo " + encodedScript + " | base64 -d > " + pyPath);
//            cmdList.add("python3 " + pyPath);
//            cmdListMap.put(id, cmdList);
//        }
//
//        List<String> attackerCmdList = cmdListMap.get(attackerId);
//        restoreCmdList.add(attackerCmdList.get(0).replace("network", "no network"));
//        restoreCmdList.add(attackerCmdList.get(1).replace("add", "del"));
//
//        InteractionManager.clearWsResponse();
//        try {
//            InteractionManager.sendCmdList(victimNodeId, cmdListMap.get(victimNodeId));
//            TimeUnit.SECONDS.sleep(5);
//            InteractionManager.sendCmdList(customerNodeId, cmdListMap.get(customerNodeId), WsOptions.REAL_TIME_OUTPUT);
//            TimeUnit.SECONDS.sleep(10);
//            InteractionManager.sendCmdList(attackerId, attackerCmdList);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//        postAttack(getAttackLog(victimNodeId, attackProcess));
//
//        return true;
//    }
//
//    private AttackLog getAttackLog(Integer victimNodeId, String attackProcess) {
//        AttackLog attackLog = new AttackLog();
//        attackLog.setAttackers(List.of(getAttackerId()));
//        attackLog.setVictims(new ArrayList<>(victimNodeId));
//        attackLog.setAttackProcess(attackProcess);
//
//        return attackLog;
//    }
//}