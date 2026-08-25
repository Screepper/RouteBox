package com.routebox.controller.http;

import com.routebox.common.constants.ConfConstants;
import com.routebox.common.response.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @Description
 * @Date 2025/12/17
**/
@RestController
@RequestMapping("/config")
@Slf4j
public class ConfigController {
    @RequestMapping("/getGfUrl")
    public R<String> getGfUrl() {
        String gfIp = ConfConstants.gfIp();
        int gfPort = ConfConstants.gfPort();

        if (checkDockerDeployment(gfIp)) return R.ok("http://" + "127.0.0.1" + ":" + gfPort);
        if (!checkBadHost(gfIp)) return R.ok("http://" + gfIp + ":" + gfPort);

        String localGfUrl = getLocalGfUrl(gfPort);
        if (localGfUrl != null) return R.ok(localGfUrl);

        return R.error("Cannot find local Grafana URL");
    }

    /**
     * @Description Check if the Grafana IP is the Docker container IP
     * @Date 2026/1/24
     **/
    private boolean checkDockerDeployment(String gfIp) {
        return "routebox-grafana".equals(gfIp);
    }

    /**
     * @Description Check if the host is a "loop" host (which cannot be used to access the service from outside)
     * @Date 2025/12/17
     **/
    private boolean checkBadHost(String host) {
        return "0.0.0.0".equals(host) || "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
    }


    /**
     * @Description Get the local Grafana URL
     * @Date 2025/12/17
     **/
    private String getLocalGfUrl(int port) {
        InetAddress address = null;

        try {
            address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            log.error("getLocalGfUrl error", e);
        }

        return address == null ? null : "http://" + address.getHostAddress() + ":" + port;
    }
}
