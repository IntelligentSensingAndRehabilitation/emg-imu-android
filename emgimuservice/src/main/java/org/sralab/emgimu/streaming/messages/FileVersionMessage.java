package org.sralab.emgimu.streaming.messages;

public class FileVersionMessage {

    public final String MSG = "FileVersion";
    public String version;

    public FileVersionMessage(String version) {
        this.version = version;
    }
}
