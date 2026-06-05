package com.minelauncher.utils;

import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Utilitário para gerar um identificador único da máquina (para salt de criptografia).
 */
public class MachineIdentifier {

    public static String getUniqueId() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) sb.append(String.format("%02X", b));
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            // Fallback para nome do host/usuário se não encontrar MAC
        }
        return System.getProperty("user.name") + System.getProperty("os.name");
    }
}
