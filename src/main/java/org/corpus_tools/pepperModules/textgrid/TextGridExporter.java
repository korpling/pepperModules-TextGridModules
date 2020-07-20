package org.corpus_tools.pepperModules.textgrid;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.corpus_tools.pepper.common.DOCUMENT_STATUS;
import org.corpus_tools.pepper.common.PepperConfiguration;
import org.corpus_tools.pepper.impl.PepperExporterImpl;
import org.corpus_tools.pepper.impl.PepperMapperImpl;
import org.corpus_tools.pepper.modules.PepperExporter;
import org.corpus_tools.pepper.modules.PepperMapper;
import org.corpus_tools.pepper.modules.exceptions.PepperModuleDataException;
import org.corpus_tools.salt.common.SDocument;
import org.corpus_tools.salt.common.SDocumentGraph;
import org.corpus_tools.salt.common.SMedialRelation;
import org.corpus_tools.salt.common.SSpan;
import org.corpus_tools.salt.common.STextualDS;
import org.corpus_tools.salt.common.SToken;
import org.corpus_tools.salt.core.SAnnotation;
import org.corpus_tools.salt.core.SNode;
import org.corpus_tools.salt.core.SRelation;
import org.corpus_tools.salt.graph.Identifier;
import org.corpus_tools.salt.util.DataSourceSequence;
import org.eclipse.emf.common.util.URI;
import org.osgi.service.component.annotations.Component;
import org.praat.Interval;
import org.praat.IntervalTier;
import org.praat.PraatFile;
import org.praat.TextGrid;
import org.praat.Tier;

@Component(name = "TextGridExporterComponent", factory = "PepperExporterComponentFactory")
public class TextGridExporter extends PepperExporterImpl implements PepperExporter {
	public TextGridExporter() {
		super();
		setName("TextGridExporter");
		setSupplierContact(URI.createURI(PepperConfiguration.EMAIL));
		setSupplierHomepage(URI.createURI(PepperConfiguration.HOMEPAGE));
		setDesc("Exports a Salt graph to PRAAT's TextGrid.");
		addSupportedFormat("TextGrid", "1.0", null);
		setDocumentEnding("TextGrid");
		setExportMode(EXPORT_MODE.DOCUMENTS_IN_FILES);
	}
	
	public PepperMapper createPepperMapper(Identifier identifier) {
		TextGridExportMapper mapper = new TextGridExportMapper();
		if (identifier.getIdentifiableElement() != null && identifier.getIdentifiableElement() instanceof SDocument) {
			URI resource = getIdentifier2ResourceTable().get(identifier);
			mapper.setResourceURI(resource);
		}
		return (mapper);
	}
	
	public static class TextGridExportMapper extends PepperMapperImpl implements PepperMapper {
		
		private static final String ERR_NO_DATA = "No data was provided (document or document graph is NULL).";
		private static final String ERR_NO_TIME_INFORMATION = "No medial relation was detected, time values cannot be computed.";
		private static final String ERR_IO = "An error occured when writing the textgrid file.";
		
		private Map<SNode, SMedialRelation> sNode2mRel = null;
		private Map<String, List<Interval>> tiername2Intervals = null;
		
		public TextGridExportMapper() {
			super();
			sNode2mRel = new HashMap<>();
			tiername2Intervals = new HashMap<>();
		}
		
		@Override
		public DOCUMENT_STATUS mapSDocument() {
			if (getDocument() == null || getDocument().getDocumentGraph() == null) {
				throw new PepperModuleDataException(this, ERR_NO_DATA);
			}
			mapTokensAndAnnotations();
			mapSpanAnnotations();
			write();
			return (DOCUMENT_STATUS.COMPLETED);
		}

		private void mapTokensAndAnnotations() {
			SDocumentGraph documentGraph = getDocument().getDocumentGraph();
			for (STextualDS ds : documentGraph.getTextualDSs()) {
				String tierName = ds.getName();
				List<SToken> tokens = documentGraph.getSortedTokenByText( documentGraph.getTokensBySequence(new DataSourceSequence<Number>(ds, ds.getStart(), ds.getEnd())) );
				double start; {
					SMedialRelation mRel = getMedialRelation(tokens.get(0));
					start = mRel.getStart();
				}
				List<Interval> intervals = getTier(tierName);
				for (SToken sTok : tokens) {
					SMedialRelation mRel = getMedialRelation(sTok);
					intervals.add(new Interval(mRel.getStart(), mRel.getEnd(), documentGraph.getText(sTok)));
					for (SAnnotation sAnno : sTok.getAnnotations()) {
						String annoName = sAnno.getName();
						List<Interval> annoTier = getTier(annoName);
						annoTier.add(new Interval(mRel.getStart(), mRel.getEnd(), sAnno.getValue_STEXT()));
					}
				}
			}
		}
		
		private SMedialRelation getMedialRelation(SNode node) {
			SMedialRelation mRel = sNode2mRel.get(node);
			if (mRel != null) {
				return mRel;
			}
			for (SRelation<?, ?> rel : node.getOutRelations()) {
				if (rel instanceof SMedialRelation) {
					mRel = (SMedialRelation) rel;
					break;
				}
			}
			if (mRel == null) {
				throw new PepperModuleDataException(this, ERR_NO_TIME_INFORMATION);
			}
			sNode2mRel.put(node, mRel);
			return mRel;
		}
		
		private List<Interval> getTier(String name) {
			List<Interval> intervals = tiername2Intervals.get(name);
			if (intervals == null) {
				intervals = new ArrayList<Interval>();
				tiername2Intervals.put(name, intervals);
			}
			return intervals;
		}

		private void mapSpanAnnotations() {
			SDocumentGraph documentGraph = getDocument().getDocumentGraph();
			for (SSpan sSpan : documentGraph.getSpans()) {
				List<SToken> tokens = documentGraph.getSortedTokenByText( documentGraph.getOverlappedTokens(sSpan) );
				double start = getMedialRelation(tokens.get(0)).getStart();				
				double end = getMedialRelation(tokens.get( tokens.size() - 1 )).getEnd();
				for (SAnnotation sAnno : sSpan.getAnnotations()) {
					String annoName = sAnno.getName();
					List<Interval> annoTier = getTier(annoName);
					annoTier.add(new Interval(start, end, sAnno.getValue_STEXT()));
				}
			}
		}

		private void write() {
			List<Tier> tiers = new ArrayList<>(); {
				for (Entry<String, List<Interval>> entry : tiername2Intervals.entrySet()) {
					String name = entry.getKey();
					List<Interval> intervals = entry.getValue();
					IntervalTier tier = new IntervalTier(name, intervals);
					tiers.add(tier);				
				}
			}
			TextGrid textgrid = new TextGrid(getDocument().getName(), tiers);
			File outputFile = null;
			if (getResourceURI().toFileString() != null) {
				outputFile = new File(getResourceURI().toFileString());
			} else {
				outputFile = new File(getResourceURI().toString());
			}
			try {
				PraatFile.writeText(textgrid, outputFile);
			} catch (IOException e) {
				throw new PepperModuleDataException(this, ERR_IO);
			}
		}
	}
}
