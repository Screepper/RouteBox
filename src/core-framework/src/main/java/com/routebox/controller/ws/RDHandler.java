package com.routebox.controller.ws;

import com.routebox.common.enums.DetectStage;
import com.routebox.common.response.WsR;
import com.routebox.util.JsonUtil;
import com.routebox.util.WsMesUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @Description Detection WebSocket Controller
 * @Date 2025/12/15
**/
@Component
@Slf4j
public class RDHandler extends TextWebSocketHandler {

    private final ThreadPoolTaskExecutor wsMessageExecutor;
    private final ConcurrentHashMap<String, Future<?>> sessionFutureMap = new ConcurrentHashMap<>();

    public RDHandler(@Qualifier("wsMessageExecutor") ThreadPoolTaskExecutor wsMessageExecutor) {
        this.wsMessageExecutor = wsMessageExecutor;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String sessionId = session.getId();
        log.info("WebSocket connection established: {}", sessionId);

        // submit the connection to the thread pool
        Future<?> future = wsMessageExecutor.submit(() -> {
            try {
                while (session.isOpen() && !Thread.currentThread().isInterrupted()) {
                    if (!WsMesUtil.isRunning()) { // wait for detection
                        sendMessage(session, new WsR(DetectStage.WAIT));
                        TimeUnit.MILLISECONDS.sleep(200);
                    } else { // detecting
                        WsR wsR = WsMesUtil.pop();
                        if (wsR == null) {
                            TimeUnit.MILLISECONDS.sleep(100);
                            continue;
                        }
                        sendMessage(session, wsR);
                    }
                }
            } catch (InterruptedException e) {
                log.info("Message sender task interrupted for session: {}", sessionId);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error sending message to session: {}", sessionId, e);
            } finally {
                sessionFutureMap.remove(sessionId); // remove the future after completion
            }
        });

        sessionFutureMap.put(sessionId, future); // save the future for cancellation
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String sessionId = session.getId();
        log.info("WebSocket connection closed: {} with status {}", sessionId, status);
        cancelTask(sessionId); // cancel the task after connection closed
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        String sessionId = session.getId();
        log.error("WebSocket transport error for session {}", sessionId, exception);
        cancelTask(sessionId); // cancel the task after transport error
    }

    /**
     * @Description Cancel the task for the session
     * @Date 2026/3/14
     **/
    private void cancelTask(String sessionId) {
        Future<?> future = sessionFutureMap.remove(sessionId);
        if (future != null) {
            future.cancel(true); // interrupt the running task
        }
    }

    /**
     * @Description Send message to the session
     * @Date 2026/3/14
     **/
    private void sendMessage(WebSocketSession session, WsR wsR) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(JsonUtil.toJson(wsR)));
            }
        } catch (Exception e) {
            log.error("Failed to send message to session: {}", session.getId(), e);
            try {
                session.close(); // close the session if send failed
            } catch (Exception ex) {
                log.error("Failed to close session: {}", session.getId(), ex);
            }
        }
    }
}