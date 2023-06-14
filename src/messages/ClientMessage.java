package messages;

import java.io.Serializable;
import java.util.UUID;

public record ClientMessage(String message, UUID usid) implements Serializable {
}
