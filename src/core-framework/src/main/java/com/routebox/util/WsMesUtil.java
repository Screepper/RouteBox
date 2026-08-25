package com.routebox.util;

import com.routebox.common.enums.DetectStage;
import com.routebox.common.response.WsR;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * @Description Save WebSocket Messages while Detection Running
 * @Date 2025/12/15
**/

public class WsMesUtil {

    private static boolean RUNNING = false; // Read-time Detection Running

    private static final ConcurrentLinkedQueue<WsR> rMesQueue = new ConcurrentLinkedQueue<>(); // Realtime Detection Message Queue

    public static boolean isRunning() {
        return RUNNING;
    }

    public static void start(String desc) {
        RUNNING = true;
        rMesQueue.clear();

        push(new WsR(DetectStage.START, desc));
    }

    public static void stop() {
        push(new WsR(DetectStage.STOP));

        new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            RUNNING = false;
        }).start();
    }


    public static void push(WsR mes) {
        if (mes == null) return;
        rMesQueue.offer(mes);
    }

    public static WsR pop() {
        return rMesQueue.poll();
    }
}