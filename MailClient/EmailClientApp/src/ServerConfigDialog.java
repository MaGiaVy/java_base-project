import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Hộp thoại cấu hình máy chủ tùy chỉnh (dành cho Mail Server phòng lab SGU hoặc máy ảo nội bộ). */
public class ServerConfigDialog extends JDialog {
    private final JTextField smtpHostField = new JTextField(15);
    private final JTextField smtpPortField = new JTextField(5);
    private final JComboBox<Security> smtpSecBox = new JComboBox<>(Security.values());

    private final JTextField pop3HostField = new JTextField(15);
    private final JTextField pop3PortField = new JTextField(5);
    private final JComboBox<Security> pop3SecBox = new JComboBox<>(Security.values());

    private final JTextField imapHostField = new JTextField(15);
    private final JTextField imapPortField = new JTextField(5);
    private final JComboBox<Security> imapSecBox = new JComboBox<>(Security.values());

    private boolean saved = false;

    public ServerConfigDialog(Frame owner, AccountConfig current) {
        super(owner, "Cấu Hình Máy Chủ Mail (Custom Server)", true);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(244, 246, 240));

        JPanel form = new JPanel(new GridLayout(3, 1, 8, 8));
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        form.add(createSection("1. Máy chủ gửi thư (SMTP)", smtpHostField, current.smtpHost,
                smtpPortField, String.valueOf(current.smtpPort), smtpSecBox, current.smtpSecurity));
        form.add(createSection("2. Máy chủ nhận thư (POP3)", pop3HostField, current.pop3Host,
                pop3PortField, String.valueOf(current.pop3Port), pop3SecBox, current.pop3Security));
        form.add(createSection("3. Máy chủ nhận thư (IMAP)", imapHostField, current.imapHost,
                imapPortField, String.valueOf(current.imapPort), imapSecBox, current.imapSecurity));

        add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setOpaque(false);
        JButton defaultBtn = new JButton("Khôi phục Gmail");
        defaultBtn.setBackground(new Color(218, 226, 214));
        defaultBtn.addActionListener(e -> resetToGmail());

        JButton saveBtn = new JButton("Lưu cấu hình");
        saveBtn.setBackground(new Color(45, 90, 67));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD));
        saveBtn.addActionListener(e -> {
            saved = true;
            dispose();
        });

        JButton cancelBtn = new JButton("Hủy bỏ");
        cancelBtn.addActionListener(e -> dispose());

        btnPanel.add(defaultBtn);
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);
        add(btnPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel createSection(String title, JTextField hostField, String hostVal,
                                 JTextField portField, String portVal,
                                 JComboBox<Security> secBox, Security secVal) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBackground(new Color(234, 239, 230));
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(90, 143, 112)), title));

        hostField.setText(hostVal);
        portField.setText(portVal);
        secBox.setSelectedItem(secVal);

        p.add(new JLabel("Host:"));
        p.add(hostField);
        p.add(new JLabel("Cổng:"));
        p.add(portField);
        p.add(new JLabel("Bảo mật:"));
        p.add(secBox);
        return p;
    }

    private void resetToGmail() {
        smtpHostField.setText("smtp.gmail.com");
        smtpPortField.setText("465");
        smtpSecBox.setSelectedItem(Security.SSL);

        pop3HostField.setText("pop.gmail.com");
        pop3PortField.setText("995");
        pop3SecBox.setSelectedItem(Security.SSL);

        imapHostField.setText("imap.gmail.com");
        imapPortField.setText("993");
        imapSecBox.setSelectedItem(Security.SSL);
    }

    public boolean isSaved() {
        return saved;
    }

    public void applyTo(AccountConfig cfg) {
        if (!saved) return;
        cfg.smtpHost = smtpHostField.getText().trim();
        cfg.smtpPort = Integer.parseInt(smtpPortField.getText().trim());
        cfg.smtpSecurity = (Security) smtpSecBox.getSelectedItem();

        cfg.pop3Host = pop3HostField.getText().trim();
        cfg.pop3Port = Integer.parseInt(pop3PortField.getText().trim());
        cfg.pop3Security = (Security) pop3SecBox.getSelectedItem();

        cfg.imapHost = imapHostField.getText().trim();
        cfg.imapPort = Integer.parseInt(imapPortField.getText().trim());
        cfg.imapSecurity = (Security) imapSecBox.getSelectedItem();
    }
}
