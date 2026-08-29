package com.ollamaster;

import android.app.Application;

public class App extends Application {
    public static App inst;

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        Personas.ensureSeed(this);
        Skills.seedPresets(this);
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.FileWriter fw = new java.io.FileWriter(
                        new java.io.File(getFilesDir(), "crash.log"), true);
                fw.write("\n==== " + new java.util.Date() + " ====\n");
                java.io.PrintWriter pw = new java.io.PrintWriter(fw);
                e.printStackTrace(pw);
                pw.flush();
                fw.close();
            } catch (Exception ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
