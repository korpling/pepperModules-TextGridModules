package org.corpus_tools.pepperModules.textgrid.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class TextGridItem implements TextGridDictionary {
	private static final char INDENT = '\t';
	private double xmin;
	protected double xmax;
	private final String value;
	private int index;
	private List<TextGridItem> entries;
	
	public TextGridItem(double xmin, double xmax, String value, int index) {
		this.xmin = xmin;
		this.xmax = xmax;
		this.value = value;
		this.index = index;
	}
	
	public double getXMin() {
		return this.xmin;
	}
	
	public double getXMax() {
		return this.xmax;
	}
	
	public void add(double xmin, double xmax, String value) {
		if (entries == null) {
			entries = new ArrayList<>();
		}
		entries.add(new TextGridItem(xmin, xmax, value, entries.size()));
	}
	
	/**
	 * Expensive, use with care.
	 */
	public void reorganize() {
		if (entries == null) {
			return;
		}
		Collections.sort(entries, new Comparator<TextGridItem>() {
			
			@Override
			public int compare(TextGridItem arg0, TextGridItem arg1) {
				return Double.compare(arg0.xmin, arg1.xmin);
			}
		});
		int ix = 0;
		for (TextGridItem itm : entries) {
			itm.index = ix++;
		}
		this.xmin = entries.get(0).xmin;
		this.xmax = entries.get( entries.size() - 1 ).xmax;
	}
	
	public void serialize(StringBuilder b) {
		serialize(b, 1);
	}
	
	protected void serialize(StringBuilder b, int depth) {
		String retVal; {
			if (entries == null) {
				write(b, depth, String.format(F_INTERVALS, this.index));
				write(b, depth + 1, String.format(F_KEY_VAL, XMIN, this.xmin));
				write(b, depth + 1, String.format(F_KEY_VAL, XMAX, this.xmax));
				write(b, depth + 1, String.format(F_KEY_VAL, TEXT, this.value));
			} else {
				write(b, depth, String.format(F_ITEM, this.index));
				write(b, depth + 1, CLASS_LINE);
				write(b, depth + 1, String.format(F_KEY_VAL, NAME, this.value));
				write(b, depth + 1, String.format(F_KEY_VAL, XMIN, this.xmin));
				write(b, depth + 1, String.format(F_KEY_VAL, XMAX, this.entries.get( this.entries.size() - 1 ).xmax));
				write(b, depth + 1, String.format(F_KEY_VAL, IV_SIZE, this.entries.size()));
				for (TextGridItem entry : entries) {
					entry.serialize(b, depth + 1);
				}
			}
		}
	}
	
	public static void writeHeader(StringBuilder b, Collection<TextGridItem> tiers) {
		b.append(StringUtils.join(HEADER, System.lineSeparator()));
		b.append(StringUtils.repeat(System.lineSeparator(), 2));
		double xmin = 0.0;
		double xmax = 0.0;
		for (TextGridItem itm : tiers) {
			xmin = itm.getXMin() < xmin? itm.getXMin() : xmin;
			xmax = itm.getXMax() > xmax? itm.getXMax() : xmax;
		}
		TextGridItem.write(b, 0, String.format(TextGridItem.F_KEY_VAL, TextGridItem.XMIN, xmin));
		TextGridItem.write(b, 0, String.format(TextGridItem.F_KEY_VAL, TextGridItem.XMAX, xmax));
		TextGridItem.write(b, 0, TextGridItem.TIERS_EXISTS);
		TextGridItem.write(b, 0, String.format(TextGridItem.F_KEY_VAL, TextGridItem.SIZE, tiers));
		TextGridItem.write(b, 0, String.format(TextGridItem.F_ITEM, ""));
		for (TextGridItem itm : tiers) {
			itm.serialize(b);
		}
	}
	
	public static void write(StringBuilder b, int indentation, String text) {
		b.append(StringUtils.repeat(INDENT, indentation));
		b.append(text);
		b.append(System.lineSeparator());
	}
}
