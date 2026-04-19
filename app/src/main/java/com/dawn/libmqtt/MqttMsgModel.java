package com.dawn.libmqtt;

public class MqttMsgModel {
    private String cmd;//指令
    private String data;//数据

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
