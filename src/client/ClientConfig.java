package client;

import java.io.File;
import java.io.InputStream;
import java.util.*;

public class ClientConfig {
    public static String getFieldValue(Field field) {
        return Fields.get(field);
    };

    public enum Field {
        XML
    }

    private static Field parseField(String field) {
        switch (field) {
            case ("XML") -> { return Field.XML; }

            default -> throw new RuntimeException("Bad configuration file: " + ConfigFile);
        }
    }

    private static final String ConfigFile = "config.txt";
    private static final Map<Field, String> Fields = Collections.synchronizedMap(new EnumMap<>(Field.class));

    static {
        InputStream in = ClientConfig.class.getResourceAsStream(ConfigFile);
        if (in == null)
            throw new RuntimeException("cannot open configuration file: " + ConfigFile);
        else {
            Scanner scanner = new Scanner(in);
            while (scanner.hasNext()) {
                String[] pair = scanner.nextLine().split("\\s*=\\s*");
                if (pair.length < 2)
                    throw new RuntimeException("bad configuration file " + ConfigFile);
                Field field = parseField(pair[0]);
                Fields.put(field, pair[1]);
            }
        }
    }
}
