package org.corpus_tools.pepperModules.textgrid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.corpus_tools.pepper.common.DOCUMENT_STATUS;
import org.corpus_tools.pepper.common.PepperConfiguration;
import org.corpus_tools.pepper.impl.PepperExporterImpl;
import org.corpus_tools.pepper.impl.PepperMapperImpl;
import org.corpus_tools.pepper.modules.PepperExporter;
import org.corpus_tools.pepper.modules.PepperMapper;
import org.corpus_tools.pepper.modules.PepperExporter.EXPORT_MODE;
import org.corpus_tools.pepper.modules.exceptions.PepperModuleDataException;
import org.corpus_tools.pepperModules.textgrid.model.TextGridItem;
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
		
		private List<TextGridItem> items = null;
		private Map<SNode, SMedialRelation> sNode2mRel = null;
		private Map<String, TextGridItem> tiername2Item = null;
		
		public TextGridExportMapper() {
			super();
			items = new ArrayList<>();
			sNode2mRel = new HashMap<>();
			tiername2Item = new HashMap<>();
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
				TextGridItem tier = getTier(tierName, start);
				for (SToken sTok : tokens) {
					SMedialRelation mRel = getMedialRelation(sTok);
					tier.add(mRel.getStart(), mRel.getEnd(), documentGraph.getText(sTok));
					for (SAnnotation sAnno : sTok.getAnnotations()) {
						String annoName = sAnno.getName();
						TextGridItem annoTier = getTier(annoName, mRel.getStart());
						annoTier.add(mRel.getStart(), mRel.getEnd(), sAnno.getValue_STEXT());
					}
				}
			}
		}
		
		private SMedialRelation getMedialRelation(SNode node) {
			SMedialRelation mRel = sNode2mRel.get(node);
			if (mRel != null) {
				return mRel;
			}
			for (SRelation rel : node.getOutRelations()) {
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
		
		private TextGridItem getTier(String name, double start) {
			TextGridItem tier = tiername2Item.get(name);
			if (tier == null) {
				tier = new TextGridItem(start, -1.0, name, tiername2Item.size());
				tiername2Item.put(name, tier);
			}
			return tier;
		}

		private void mapSpanAnnotations() {
			SDocumentGraph documentGraph = getDocument().getDocumentGraph();
			Set<String> reorganizeNames = new HashSet<>();
			for (SSpan sSpan : documentGraph.getSpans()) {
				List<SToken> tokens = documentGraph.getSortedTokenByText( documentGraph.getOverlappedTokens(sSpan) );
				double start = getMedialRelation(tokens.get(0)).getStart();				
				double end = getMedialRelation(tokens.get( tokens.size() - 1 )).getEnd();
				for (SAnnotation sAnno : sSpan.getAnnotations()) {
					String annoName = sAnno.getName();
					TextGridItem annoTier = getTier(annoName, start);
					annoTier.add(start, end, sAnno.getValue_STEXT());
					reorganizeNames.add(annoName);
				}
			}
			for (String name : reorganizeNames) {
				tiername2Item.get(name).reorganize();
			}
		}

		private void write() {
			StringBuilder builder = new StringBuilder();
			TextGridItem.writeHeader(builder, tiername2Item.values());
			File outputFile = null;
			if (getResourceURI().toFileString() != null) {
				outputFile = new File(getResourceURI().toFileString());
			} else {
				outputFile = new File(getResourceURI().toString());
			}
			OutputStream outStream;
			try {
				outStream = new FileOutputStream(outputFile);
				outStream.write(builder.toString().getBytes());
				outStream.close();
			} catch (IOException e) {
				throw new PepperModuleDataException(this, ERR_IO);
			}
		}
	}
}
