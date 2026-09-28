package org.zalava.skills.application.port.out;

import java.util.List;
import org.zalava.skills.domain.SkillDescriptor;

/** Discovers skill metadata from one origin (local directory or remote catalogue). */
public interface SkillCatalog {

  List<SkillDescriptor> discover();
}
