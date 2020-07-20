package org.corpus_tools.pepperModules.textgrid.model;

public interface TextGridDictionary {
	public static final String F_INTERVALS = "intervals [{}]:";
	public static final String F_ITEM = "item [{}]:";
	public static final String F_KEY_VAL = "{} = {}";
	public static final String CLASS_LINE = "class = \"IntervalTier\"";
	public static final String NAME = "name";
	public static final String TEXT = "text";
	public static final String XMIN = "xmin";
	public static final String XMAX = "xmax";
	public static final String SIZE = "size";
	public static final String IV_SIZE = "intervals: size";
	public static final String TIERS_EXISTS = "tiers? <exists>";
	public static final String[] HEADER = {"File type = \"ooTextFile\"", "Object class = \"TextGrid\"", ""};
}
