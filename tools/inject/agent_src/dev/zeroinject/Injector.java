package dev.zeroinject;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public class Injector {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: Injector <agentJar> <modJar>");
            System.exit(1);
        }
        String agentJar = args[0];
        String modJar   = args[1];

        VirtualMachine vm = null;
        for (VirtualMachineDescriptor d : VirtualMachine.list()) {
            String name = d.displayName();
            if (name.contains("KnotClient") || name.contains("net.fabricmc") ||
                name.contains("minecraft")) {
                vm = VirtualMachine.attach(d);
                System.out.println("[ZERO injector] attached: " + name + " pid=" + d.id());
                break;
            }
        }
        if (vm == null) {
            System.out.println("[ZERO injector] no MC JVM found. running JVMs:");
            for (VirtualMachineDescriptor d : VirtualMachine.list())
                System.out.println("  pid=" + d.id() + "  " + d.displayName());
            System.exit(1);
        }
        vm.loadAgent(agentJar, modJar);
        vm.detach();
        System.out.println("[ZERO injector] agent loaded");
    }
}
