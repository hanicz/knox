package org.apache.knox.gateway.dto;

public class SSEvent {

    private final String data;
    private final String name;

    public SSEvent(String name, String data) {
        this.name = name;
        this.data = data;
    }

    public String getData() {
        return data;
    }

    @Override
    public String toString() {
        return "name: " + this.name + " data: " + this.data;
    }
}
