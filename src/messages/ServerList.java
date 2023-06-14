package messages;

import java.io.Serializable;
import java.util.ArrayList;

public record ServerList(ArrayList<ClientLogin> users) implements Serializable {

}
