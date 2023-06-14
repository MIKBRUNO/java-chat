package messages;

import java.io.Serializable;

public record ServerError(String message) implements Serializable {

}
