package com.example.client;

import com.example.client.cli.ConsoleChatClient;
import com.example.client.ui.ChatWindow;
import com.example.client.ui.LoginDialog;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Main Entry Point for Secure TCP Chat Client.
 * Supports dual UI:
 *   - Command-Line Interface (CLI): run with argument `--cli`
 *   - Graphical User Interface (GUI): run without arguments or with `--gui`
 */
public class Program {

    public static void main(String[] args) {
        boolean cliMode = false;
        for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg)) {
                cliMode = true;
                break;
            }
        }

        if (cliMode) {
            ConsoleChatClient.main(args);
        } else {
            launchGui();
        }
    }

    private static void launchGui() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fallback to default Swing Look and Feel
        }

        SwingUtilities.invokeLater(() -> {
            LoginDialog loginDialog = new LoginDialog(null);
            loginDialog.setVisible(true);

            if (loginDialog.isSucceeded() && loginDialog.getClient() != null) {
                ChatClient client = loginDialog.getClient();
                ChatWindow chatWindow = new ChatWindow(client);
                chatWindow.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
}
