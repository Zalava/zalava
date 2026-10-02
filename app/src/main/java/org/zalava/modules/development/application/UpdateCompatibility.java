package org.zalava.modules.development.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.zalava.modules.development.CandidateEvaluation;
import org.zalava.modules.development.InstalledModuleAcceptance;
import org.zalava.modules.development.ModuleDevelopmentContract;

/** Deterministic compatibility decision; approval remains outside this policy. */
public final class UpdateCompatibility {
  private UpdateCompatibility() {}

  public static Result compare(
      InstalledModuleAcceptance installed,
      ModuleDevelopmentContract candidate,
      CandidateEvaluation standaloneEvaluation,
      List<String> candidatePermissions) {
    List<String> changes = new ArrayList<>();
    if (!standaloneEvaluation.accepted())
      return new Result(Decision.INVALID, List.of("Candidate did not pass universal validation"));
    Map<String, ModuleDevelopmentContract.Tool> previous = tools(installed.exposedContract());
    Map<String, ModuleDevelopmentContract.Tool> next = tools(candidate);
    previous.forEach(
        (name, tool) -> {
          ModuleDevelopmentContract.Tool replacement = next.get(name);
          if (replacement == null) changes.add("Removed tool: " + name);
          else if (!tool.inputSchema().equals(replacement.inputSchema())
              || !tool.outputSchema().equals(replacement.outputSchema()))
            changes.add("Incompatible schema change: " + name);
        });
    if (!changes.isEmpty()) return new Result(Decision.BREAKING, List.copyOf(changes));
    Set<String> additionalPermissions =
        Set.copyOf(candidatePermissions).stream()
            .filter(permission -> !installed.grantedPermissions().contains(permission))
            .collect(Collectors.toSet());
    if (!additionalPermissions.isEmpty())
      return new Result(
          Decision.REQUIRES_APPROVAL,
          additionalPermissions.stream()
              .sorted()
              .map(permission -> "New permission: " + permission)
              .toList());
    return new Result(
        Decision.COMPATIBLE, List.of("Candidate is compatible with retained installed contract"));
  }

  private static Map<String, ModuleDevelopmentContract.Tool> tools(
      ModuleDevelopmentContract contract) {
    return contract.tools().stream()
        .collect(Collectors.toMap(ModuleDevelopmentContract.Tool::name, Function.identity()));
  }

  public enum Decision {
    COMPATIBLE,
    REQUIRES_APPROVAL,
    BREAKING,
    INVALID
  }

  public record Result(Decision decision, List<String> changes) {
    public Result {
      changes = List.copyOf(changes);
    }
  }
}
