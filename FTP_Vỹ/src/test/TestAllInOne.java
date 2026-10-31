package test;

import ui.FtpAllInOneApp;

public class TestAllInOne {
    public static void main(String[] args) {
        try {
            System.out.println("Testing FtpAllInOneApp instantiation...");
            FtpAllInOneApp app = new FtpAllInOneApp();
            System.out.println("App instantiated successfully!");
            System.out.println("Window title: " + app.getTitle());
            System.out.println("Window size: " + app.getSize());
            System.out.println("Now disposing...");
            app.dispose();
            System.out.println("ALL GOOD!");
            System.exit(0);
        } catch (Throwable t) {
            System.err.println("EXCEPTION IN FtpAllInOneApp:");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
