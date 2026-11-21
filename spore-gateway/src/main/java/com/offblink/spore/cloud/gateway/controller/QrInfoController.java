package com.offblink.spore.cloud.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;

/**
 * 扫码页的地址提示：告诉页面「手机该连的本机局域网 IP」。
 *
 * <p>手机扫完码要连的是这台机器的局域网地址，页面若跑在 127.0.0.1 上就编不出可用地址。
 * 这里用跟 {@code tools/qr-login.py} 相同的办法——朝一个不发包的 UDP 目标连一下，
 * 拿到出口网卡的地址（有多块网卡时选的是默认路由那块，不是随便挑第一块）。
 *
 * <p>不在 {@code /api/**} 下，所以不走网关路由，也不经过 {@code GatewayAuthFilter}。
 */
@RestController
public class QrInfoController {

    @GetMapping("/qr/info")
    public Map<String, String> info() {
        return Collections.singletonMap("lan", lanAddress());
    }

    private static String lanAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("223.5.5.5"), 80);
            return socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
