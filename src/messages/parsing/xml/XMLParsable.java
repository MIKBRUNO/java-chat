package messages.parsing.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public interface XMLParsable {
    void parse(Document doc, Node main);

    static void addTextNode(String nodeName, String text, Node main, Document doc) {
        Node node = doc.createElement(nodeName);
        node.appendChild(doc.createTextNode(text));
        main.appendChild(node);
    }
}
