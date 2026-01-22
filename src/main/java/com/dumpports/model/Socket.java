package com.dumpports.model;

/**
 * Data model representing a network socket.
 * Contains information about socket state, local/remote addresses, and statistics.
 */
public class Socket {

    private String netns;
    private String protocol;
    private String state;
    private String recvQueue;
    private String sendQueue;
    private String localAddress;
    private String localPort;
    private String remoteAddress;
    private String remotePort;
    private String pid;
    private String process;
    private String executablePath;

    public Socket() {
    }

    public Socket(String protocol, String state, String localAddress, String localPort,
                  String remoteAddress, String remotePort) {
        this.protocol = protocol;
        this.state = state;
        this.localAddress = localAddress;
        this.localPort = localPort;
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
    }

    // Getters and Setters
    public String getNetns() {
        return netns;
    }

    public void setNetns(String netns) {
        this.netns = netns;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getRecvQueue() {
        return recvQueue;
    }

    public void setRecvQueue(String recvQueue) {
        this.recvQueue = recvQueue;
    }

    public String getSendQueue() {
        return sendQueue;
    }

    public void setSendQueue(String sendQueue) {
        this.sendQueue = sendQueue;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public String getLocalPort() {
        return localPort;
    }

    public void setLocalPort(String localPort) {
        this.localPort = localPort;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public String getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(String remotePort) {
        this.remotePort = remotePort;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getProcess() {
        return process;
    }

    public void setProcess(String process) {
        this.process = process;
    }

    public String getExecutablePath() {
        return executablePath;
    }

    public void setExecutablePath(String executablePath) {
        this.executablePath = executablePath;
    }

    @Override
    public String toString() {
        return "Socket{" +
                "protocol='" + protocol + '\'' +
                ", state='" + state + '\'' +
                ", localAddress='" + localAddress + '\'' +
                ", localPort='" + localPort + '\'' +
                ", remoteAddress='" + remoteAddress + '\'' +
                ", remotePort='" + remotePort + '\'' +
                ", process='" + process + '\'' +
                ", executablePath='" + executablePath + '\'' +
                '}';
    }
}
