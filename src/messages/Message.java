package messages;

import messages.MessageType;

import java.io.Serializable;

public class Message implements Serializable {
    public Message(MessageType type, Serializable message) {
        Type = type;
        Message = message;
    }

    public MessageType getType() {
        return Type;
    }

    public Serializable getMessage() {
        return Message;
    }

    private final Serializable Message;
    private final MessageType Type;
}
