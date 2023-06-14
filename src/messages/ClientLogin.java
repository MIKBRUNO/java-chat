package messages;

import messages.parsing.xml.XMLParsable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.Serializable;

public record ClientLogin(String name, String client) implements Serializable, XMLParsable {
    @Override
    public void parse(Document doc, Node main) {
        XMLParsable.addTextNode("name", name(), main, doc);
        XMLParsable.addTextNode("type", client(), main, doc);
    }
}
