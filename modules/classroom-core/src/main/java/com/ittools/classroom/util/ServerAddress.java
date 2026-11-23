package com.ittools.classroom.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** 学生、掌控板连平台用的地址：浏览器里用 localhost 打开时（老师在本机上），换成本机的局域网 IPv4，掌控板才能用。 */
public final class ServerAddress {
    private ServerAddress() {}

    public static String forBoards(String host) {
        if (host == null || host.isEmpty() || "localhost".equalsIgnoreCase(host) || host.startsWith("127.") || "::1".equals(host)
                || "0:0:0:0:0:0:0:1".equals(host)) {
            String lan = lanIp();
            return lan != null ? lan : host;
        }
        return host;
    }

    /** 第一个可用的局域网 IPv4（跳过回环、虚拟网卡常用的 169.254 段和未启用的网卡）。 */
    public static String lanIp() {
        try {
            String fallback = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = (ni.getDisplayName() + " " + ni.getName()).toLowerCase();
                boolean virtual = name.contains("virtual") || name.contains("vmware") || name.contains("vbox") || name.contains("hyper-v")
                        || name.contains("wsl") || name.contains("docker") || name.contains("vethernet");
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress() || a.isLinkLocalAddress()) continue;
                    if (!virtual && a.isSiteLocalAddress()) return a.getHostAddress();
                    if (fallback == null) fallback = a.getHostAddress();
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }
}
