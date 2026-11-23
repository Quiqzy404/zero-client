package dev.zeroinject;

import java.lang.instrument.Instrumentation;
import java.nio.file.Paths;

public class Agent {
    public static void agentmain(String agentArgs, Instrumentation inst) {
        try {
            java.nio.file.Path modPath = Paths.get(agentArgs);

            Class<?> launcherBase = null;
            for (Class<?> c : inst.getAllLoadedClasses()) {
                if ("net.fabricmc.loader.impl.launch.FabricLauncherBase".equals(c.getName())) {
                    launcherBase = c;
                    break;
                }
            }
            if (launcherBase == null) {
                System.out.println("[ZERO agent] FabricLauncherBase not found");
                return;
            }

            Object launcher = launcherBase.getMethod("getLauncher").invoke(null);
            launcher.getClass()
                .getMethod("addToClassPath", java.nio.file.Path.class, String[].class)
                .invoke(launcher, modPath, new String[0]);
            System.out.println("[ZERO agent] classpath += " + modPath);

            ClassLoader cl = (ClassLoader) launcher.getClass()
                .getMethod("getTargetClassLoader").invoke(launcher);

            Thread.sleep(300);

            // Payload lives inside the mod jar; it guards against double-init itself
            Class<?> payload = Class.forName("dev.zeroinject.payload.Payload", true, cl);
            payload.getMethod("init").invoke(null);
            System.out.println("[ZERO agent] done");

        } catch (Throwable t) {
            System.out.println("[ZERO agent] failed: " + t);
            t.printStackTrace(System.out);
        }
    }
}
