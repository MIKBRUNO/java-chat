package messages;

import java.io.Serializable;
import java.util.UUID;

public record ServerClientSessionID(UUID usid) implements Serializable {
}
