package org.corpus_tools.pepperModules.textgrid;

import java.util.LinkedHashSet;
import java.util.Set;

import org.corpus_tools.pepper.modules.PepperModuleProperties;
import org.corpus_tools.pepper.modules.PepperModuleProperty;

import com.google.common.base.Splitter;

public class TextGridImporterProperties extends PepperModuleProperties {

	public static final String PROP_PRIMARY_TEXT = "primText";

	public TextGridImporterProperties() {
		addProperty(new PepperModuleProperty<>(PROP_PRIMARY_TEXT, String.class, "Defines the name of the tier(s), that hold the primary text, this can either be a single column name, or a comma seperated enumeration of column names (key: 'primText', default value: 'tok').", "tok", false));
	}
	
	
	public Set<String> getPrimaryText() {
		String rawValue = (String) getProperty(PROP_PRIMARY_TEXT).getValue();
		return new LinkedHashSet<String>(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(rawValue));
	}
}
