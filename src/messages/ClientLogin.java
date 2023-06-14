package messages;

import java.io.Serializable;

public record ClientLogin(String name, String client) implements Serializable {
    
}
