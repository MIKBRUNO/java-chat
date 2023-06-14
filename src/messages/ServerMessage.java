package messages;

import java.io.Serializable;
import java.util.UUID;

public record ServerMessage(String message, String name) implements Serializable {
}
