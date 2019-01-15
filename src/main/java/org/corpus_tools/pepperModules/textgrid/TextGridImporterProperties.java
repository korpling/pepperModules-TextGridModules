package org.corpus_tools.pepperModules.textgrid;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.corpus_tools.pepper.modules.PepperModuleProperties;
import org.corpus_tools.pepper.modules.PepperModuleProperty;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class TextGridImporterProperties extends PepperModuleProperties {

	public static final String PROP_ANNO_PRIM_REL = "annoPrimRel";
	public static final String PROP_MAP_UNKNOWN_AS_TOKEN = "mapUnknownAsToken";
	public static final String PROP_AUDIO_EXTENSION = "audioExtension";

	public TextGridImporterProperties() {
		addProperty(new PepperModuleProperty<>(PROP_ANNO_PRIM_REL, String.class,
				"Defines which primary text tiers are the basis of which annotation tiers, therefor a comma seperated list of primary text tiers, followed by a list of all annotations that refer to the primary tier, is needed. A possible key-value set could be: key='shortAnnoPrimRel', value='primText1={anno1, anno2}, primText2={anno3}' (key: 'annoPrimRel', default is 'null').",
				"", false));
		addProperty(new PepperModuleProperty<>(PROP_MAP_UNKNOWN_AS_TOKEN, Boolean.class,
				"If set to true, annotation layer which are not configured via the annoPrimRel parameter are mapped as token.",
				true, false));
		addProperty(new PepperModuleProperty<>(PROP_AUDIO_EXTENSION, String.class,
				"Extension of the linked audio files", ".wav"));
	}

	public Map<String, String> getAnnoPrimRel() {
		Multimap<String, String> prim2anno = HashMultimap.create();
		String rawValue = (String) getProperty(PROP_ANNO_PRIM_REL).getValue();
		for (String def : Splitter.on(';').trimResults().omitEmptyStrings().split(rawValue)) {
			// split before = and values after
			List<String> keyValue = Splitter.on('=').limit(2).splitToList(def);
			if (keyValue.size() == 2) {
				// the the list of annotation names
				String prim = keyValue.get(0);
				String value = StringUtils.strip(keyValue.get(1), "{}");
				for (String anno : Splitter.on(',').trimResults().omitEmptyStrings().split(value)) {
					prim2anno.put(prim, anno);
				}
			}
		}

		Map<String, String> anno2prim = new TreeMap<>();
		// reverse the map
		for (Map.Entry<String, String> e : prim2anno.entries()) {
			anno2prim.put(e.getValue(), e.getKey());
		}
		return anno2prim;
	}
	
	public String getAudioExtension() {
		return (String) getProperty(PROP_AUDIO_EXTENSION).getValue();
	}
	
	public boolean isMapUnknownAsToken() {
		return (Boolean) getProperty(PROP_MAP_UNKNOWN_AS_TOKEN).getValue();
	}
}
