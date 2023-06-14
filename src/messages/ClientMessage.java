package messages;

import messages.parsing.xml.XMLParsable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.Serializable;
import java.util.UUID;

public record ClientMessage(String message, UUID usid) implements Serializable, XMLParsable {
    @Override
    public void parse(Document doc, Node main) {
        XMLParsable.addTextNode("message", message, main, doc);
        XMLParsable.addTextNode("session", usid.toString(), main, doc);
    }
}
