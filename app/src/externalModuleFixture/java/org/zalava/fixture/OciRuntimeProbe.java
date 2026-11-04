package org.zalava.fixture;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Disposable JVM/configuration probe; never included in the application image. */
public final class OciRuntimeProbe {
  public static void main(String[] args) {
    if (args[0].equals("heap")) {
      List<byte[]> retained = new ArrayList<>();
      while (true) retained.add(new byte[1024 * 1024]);
    }
    if (args[0].equals("native")) {
      List<ByteBuffer> retained = new ArrayList<>();
      while (true) {
        ByteBuffer memory = ByteBuffer.allocateDirect(8 * 1024 * 1024);
        for (int index = 0; index < memory.capacity(); index += 4096) memory.put(index, (byte) 1);
        retained.add(memory);
      }
    }
    if (args[0].equals("resources")) {
      var application = new SpringApplication(OciRuntimeProbe.class);
      application.setWebApplicationType(WebApplicationType.NONE);
      application.setRegisterShutdownHook(false);
      application.setLogStartupInfo(false);
      try (var context = application.run(Arrays.copyOfRange(args, 1, args.length))) {
        var vm = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        System.out.println("PROBE processors=" + Runtime.getRuntime().availableProcessors());
        System.out.println("PROBE heap=" + Runtime.getRuntime().maxMemory());
        System.out.println("PROBE compact=" + vm.getVMOption("UseCompactObjectHeaders").getValue());
        System.out.println(
            "PROBE exitOnOom=" + vm.getVMOption("ExitOnOutOfMemoryError").getValue());
        System.out.println("PROBE dump=" + vm.getVMOption("HeapDumpOnOutOfMemoryError").getValue());
        System.out.println(
            "PROBE value=" + context.getEnvironment().getRequiredProperty("zalava.probe.value"));
      }
    }
  }
}
