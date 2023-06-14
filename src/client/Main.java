package client;

import messages.MessageType;
import messages.ServerError;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

public class Main {
    public static void main(String[] args) throws IOException {
        final JFrame frame = new JFrame();
        final UUID[] uuid = new UUID[1];
        Client client = new Client();
        Authorize authorizePane = new Authorize(auth -> {
            auth.getErrorLabel().setText("");
            String name = auth.getName().getText();
            String[] addressPort = auth.getAddress().getText().split("/*:/*");
            if (addressPort.length != 2) {
                auth.getErrorLabel().setText("usage: <ip-address>:<port>");
                return;
            }
            int port = 0;
            try {
                port = Integer.parseInt(addressPort[1]);
            }
            catch (NumberFormatException e) {
                auth.getErrorLabel().setText("usage: <ip-address>:<port>");
            }
            try {
                client.start(addressPort[0], port);
            } catch (Exception e) {
                auth.getErrorLabel().setText("bad address");
//                    throw new RuntimeException(e);
            }
            client.login(name);
        });
        ClientGUI gui = new ClientGUI(client);
        client.setMessageListener((me) -> {
            if (me.getType() == MessageType.SERVER_LOGIN_SUCCESS) {
                frame.setContentPane(gui.getContentPane());
                frame.setPreferredSize(new Dimension(720, 480));
                frame.pack();
            } else if (me.getType() == MessageType.SERVER_ERROR) {
                JOptionPane.showMessageDialog(frame, "Server error: " + ((ServerError) me.getMessage()).message());
            }
            gui.onMessage(me);
        });
        client.setCloseListener(() -> {
            frame.setContentPane(authorizePane.getContentPane());
            frame.pack();
            JOptionPane.showMessageDialog(frame, "Connection refused");
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.logout();
            }
        });
        frame.setTitle("JavaChat");
        frame.setContentPane(authorizePane.getContentPane());
        frame.pack();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}