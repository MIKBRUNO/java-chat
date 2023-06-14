package server;

import java.io.File;
import java.io.InputStream;
import java.util.*;

public class ServerConfigurations {
    public static String getFieldValue(Field field) {
        return Fields.get(field);
    };

    public enum Field {
        PORT,
        LOGGING,
        XML
    }

    private static Field parseField(String field) {
        switch (field) {
            case ("port") -> { return Field.PORT; }
            case ("logging") -> { return Field.LOGGING; }
            case ("XML") -> { return Field.XML; }

            default -> throw new RuntimeException("Bad configuration file: " + ConfigFile);
        }
    }

    private static final String ConfigFile = "config.txt";
    private static final Map<Field, String> Fields = Collections.synchronizedMap(new EnumMap<>(Field.class));

    static {
        InputStream in = ServerConfigurations.class.getResourceAsStream(ConfigFile);
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
