package org.corpus_tools.pepperModules.textgrid;

import java.util.LinkedHashSet;
import java.util.Set;

import org.corpus_tools.pepper.modules.PepperModuleProperties;
import org.corpus_tools.pepper.modules.PepperModuleProperty;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class TextGridImporterProperties extends PepperModuleProperties {

	public static final String PROP_PRIMARY_TEXT = "primText";
	public static final String PROP_ANNO_SHORT_PRIM_REL = "shortAnnoPrimRel";

	public TextGridImporterProperties() {
		addProperty(new PepperModuleProperty<>(PROP_PRIMARY_TEXT, String.class, "Defines the name of the tier(s), that hold the primary text, this can either be a single column name, or a comma seperated enumeration of column names (key: 'primText', default value: 'tok').", "tok", false));
		addProperty(new PepperModuleProperty<>(PROP_ANNO_SHORT_PRIM_REL, String.class, "Defines which primary text tiers are the basis of which annotation tiers, therefor a comma seperated list of primary text tiers, followed by a list of all annotations that refer to the primary tier, is needed. A possible key-value set could be: key='shortAnnoPrimRel', value='primText1={anno1, anno2}, primText2={anno3}' (key: 'annoPrimRel', default is 'null').",  null, false));
	}
	
	
	public Set<String> getPrimaryText() {
		String rawValue = (String) getProperty(PROP_PRIMARY_TEXT).getValue();
		return new LinkedHashSet<String>(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(rawValue));
	}
	
	public Multimap<String, String> getShortAnnoPrimRel() {
		Multimap<String, String> result = HashMultimap.create();
		String rawValue = (String) getProperty(PROP_ANNO_SHORT_PRIM_REL).getValue();
		for(String def : Splitter.on(',').trimResults().omitEmptyStrings().split(rawValue)) {
			// TODO: split before == and values after
		}
		return result;
	}
}
