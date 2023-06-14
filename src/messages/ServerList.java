package messages;

import messages.parsing.xml.XMLParsable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.Serializable;
import java.util.ArrayList;

public record ServerList(ArrayList<ClientLogin> users) implements Serializable, XMLParsable {
    @Override
    public void parse(Document doc, Node main) {
        Node listusers = doc.createElement("listusers");
        for (var user : users()) {
            Node u = doc.createElement("user");
            user.parse(doc, u);
            listusers.appendChild(u);
        }
        main.appendChild(listusers);
    }
}
